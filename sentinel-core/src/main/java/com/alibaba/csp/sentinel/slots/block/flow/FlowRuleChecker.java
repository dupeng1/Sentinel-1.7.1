/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.csp.sentinel.slots.block.flow;

import java.util.Collection;

import com.alibaba.csp.sentinel.cluster.ClusterStateManager;
import com.alibaba.csp.sentinel.cluster.server.EmbeddedClusterTokenServerProvider;
import com.alibaba.csp.sentinel.cluster.client.TokenClientProvider;
import com.alibaba.csp.sentinel.cluster.TokenResultStatus;
import com.alibaba.csp.sentinel.cluster.TokenResult;
import com.alibaba.csp.sentinel.cluster.TokenService;
import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.node.DefaultNode;
import com.alibaba.csp.sentinel.node.Node;
import com.alibaba.csp.sentinel.slotchain.ResourceWrapper;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.clusterbuilder.ClusterBuilderSlot;
import com.alibaba.csp.sentinel.util.StringUtil;
import com.alibaba.csp.sentinel.util.function.Function;

/**
 * Rule checker for flow control rules.
 *
 * @author Eric Zhao
 */

/**
 * 根据资源的实时指标数据检查是否达到限流规则的阈值，只要达到某个限流规则的阈值，就抛出FlowException或采用流量效果控制器处理超出阈值的流量
 */
public class FlowRuleChecker {

    public void checkFlow(Function<String, Collection<FlowRule>> ruleProvider, ResourceWrapper resource,
                          Context context, DefaultNode node, int count, boolean prioritized) throws BlockException {
        if (ruleProvider == null || resource == null) {
            return;
        }
        //1、调用FlowSlot传递过来的ruleProvider的apply方法获取当前【资源】的所有限流规则
        Collection<FlowRule> rules = ruleProvider.apply(resource.getName());
        if (rules != null) {
            //2、遍历限流规则，只要由一个限流规则达到限流阈值即可抛出FlowException
            for (FlowRule rule : rules) {
                //3、调用canPassCheck方法判断是否放行当前请求
                if (!canPassCheck(rule, context, node, count, prioritized)) {
                    //FlowException是BlockException子类，使用FlowException目的是标志当前请求因为达到限流阈值而被拒绝
                    throw new FlowException(rule.getLimitApp(), rule);
                }
            }
        }
    }

    public boolean canPassCheck(/*@NonNull*/ FlowRule rule, Context context, DefaultNode node,
                                                    int acquireCount) {
        return canPassCheck(rule, context, node, acquireCount, false);
    }

    /**
     * 检查是否允许当前请求通过，若canPassCheck方法返回true，则说明允许当前请求通过，否则不允许当前请求通过
     * @param rule
     * @param context
     * @param node
     * @param acquireCount
     * @param prioritized
     * @return
     */
    public boolean canPassCheck(/*@NonNull*/ FlowRule rule, Context context, DefaultNode node, int acquireCount,
                                                    boolean prioritized) {
        //1、指定当前限流规则只对哪个调用来源生效，默认为default，即不限定调用来源
        String limitApp = rule.getLimitApp();
        if (limitApp == null) {
            return true;
        }
        //2、指定是否是集群限流模式，如果是集群限流模式，则调用passClusterCheck方法完成canPassCheck
        if (rule.isClusterMode()) {
            return passClusterCheck(rule, context, node, acquireCount, prioritized);
        }
        //3、如果是非集群限流模式，则调用passLocalCheck方法完成canPassCheck
        return passLocalCheck(rule, context, node, acquireCount, prioritized);
    }

    private static boolean passLocalCheck(FlowRule rule, Context context, DefaultNode node, int acquireCount,
                                          boolean prioritized) {
        //根据调用来源和调用关系限流策略选择Node
        Node selectedNode = selectNodeByRequesterAndStrategy(rule, context, node);
        if (selectedNode == null) {
            return true;
        }
        //获取限流规则配置的流量效果控制器（TrafficShapingController）
        return rule.getRater()
                //调用流量效果控制器的 canPass 方法完成 canPassCheck
                .canPass(selectedNode, acquireCount, prioritized);
    }

    static Node selectReferenceNode(FlowRule rule, Context context, DefaultNode node) {
        String refResource = rule.getRefResource();
        int strategy = rule.getStrategy();

        if (StringUtil.isEmpty(refResource)) {
            return null;
        }

        if (strategy == RuleConstant.STRATEGY_RELATE) {
            //取引用资源对应调用来源的ClusterNode
            return ClusterBuilderSlot.getClusterNode(refResource);
        }

        if (strategy == RuleConstant.STRATEGY_CHAIN) {
            if (!refResource.equals(context.getName())) {
                return null;
            }
            //取当前资源的DefaultNode
            return node;
        }
        // No node.
        return null;
    }

    private static boolean filterOrigin(String origin) {
        // Origin cannot be `default` or `other`.
        return !RuleConstant.LIMIT_APP_DEFAULT.equals(origin) && !RuleConstant.LIMIT_APP_OTHER.equals(origin);
    }

    //根据调用来源和调用关系限流策略选择Node，就是根据限流规则配置limitApp与strategy选择一个Node
    static Node selectNodeByRequesterAndStrategy(/*@NonNull*/ FlowRule rule, Context context, DefaultNode node) {
        // The limit app should not be empty.
        //表示限流规则仅对指定调用来源生效
        String limitApp = rule.getLimitApp();
        //表示限流规则使用的调用关系限流策略
        int strategy = rule.getStrategy();
        //表示当前请求的调用来源
        String origin = context.getOrigin();
        //针对指定调用来源限流
        if (limitApp.equals(origin) && filterOrigin(origin)) {
            if (strategy == RuleConstant.STRATEGY_DIRECT) {
                // Matches limit origin, return origin statistic node.
                //取对应调用来源的StatisticNode
                return context.getOriginNode();
            }

            return selectReferenceNode(rule, context, node);
        }
        //针对所有调用来源限流
        else if (RuleConstant.LIMIT_APP_DEFAULT.equals(limitApp)) {
            if (strategy == RuleConstant.STRATEGY_DIRECT) {
                // Return the cluster node.
                //取当前资源的ClusterNode
                return node.getClusterNode();
            }

            return selectReferenceNode(rule, context, node);
        }
        //既不是针对所有调用来源限流，也没有规则针对当前调用来源限流，如果此时围绕该资源配置的所有限流规则都没有针对当前调用来限流，当前规则才会生效
        else if (RuleConstant.LIMIT_APP_OTHER.equals(limitApp)
            && FlowRuleManager.isOtherOrigin(origin, rule.getResource())) {
            if (strategy == RuleConstant.STRATEGY_DIRECT) {
                //取对应调用来源的StatisticNode
                return context.getOriginNode();
            }

            return selectReferenceNode(rule, context, node);
        }

        return null;
    }

    private static boolean passClusterCheck(FlowRule rule, Context context, DefaultNode node, int acquireCount,
                                            boolean prioritized) {
        try {
            //1、获取TokenService实例
            TokenService clusterService = pickClusterService();
            if (clusterService == null) {
                return fallbackToLocalOrPass(rule, context, node, acquireCount, prioritized);
            }
            //2、获取集群限流规则的全局唯一ID
            long flowId = rule.getClusterConfig().getFlowId();
            //3、调用TokenService#requestToken方法申请令牌，将方法参数构造为请求数据包，再向集群限流服务端发起请求，并同步等待获取集群限流服务端的响应结果
            TokenResult result = clusterService.requestToken(flowId, acquireCount, prioritized);
            //4、调用 applyTokenResult 方法处理响应结果
            return applyTokenResult(result, rule, context, node, acquireCount, prioritized);
            // If client is absent, then fallback to local mode.
        } catch (Throwable ex) {
            RecordLog.warn("[FlowRuleChecker] Request cluster token unexpected failed", ex);
        }
        // Fallback to local flow control when token client or server for this rule is not available.
        // If fallback is not enabled, then directly pass.
        return fallbackToLocalOrPass(rule, context, node, acquireCount, prioritized);
    }

    private static boolean fallbackToLocalOrPass(FlowRule rule, Context context, DefaultNode node, int acquireCount,
                                                 boolean prioritized) {
        if (rule.getClusterConfig().isFallbackToLocalWhenFail()) {
            return passLocalCheck(rule, context, node, acquireCount, prioritized);
        } else {
            // The rule won't be activated, just pass.
            return true;
        }
    }

    //根据节点当前角色获取TokenService实例，如果当前节点是集群限流客户端角色，则获取的TokenService实例类型是ClusterTokenClient
    //如果当前节点是集群限流服务端角色（嵌入式模式），则获取的TokenService实例类型是EmbeddedClusterTokenServer
    private static TokenService pickClusterService() {
        //集群限流客户端角色
        if (ClusterStateManager.isClient()) {
            return TokenClientProvider.getClient();
        }
        //集群限流服务端角色（嵌入式模式）
        if (ClusterStateManager.isServer()) {
            return EmbeddedClusterTokenServerProvider.getServer();
        }
        return null;
    }

    //根据响应状态码决定是否拒绝当前请求
    private static boolean applyTokenResult(/*@NonNull*/ TokenResult result, FlowRule rule, Context context,
                                                         DefaultNode node,
                                                         int acquireCount, boolean prioritized) {
        switch (result.getStatus()) {
            case TokenResultStatus.OK:
                //当响应状态码为OK时，放行请求
                return true;
            case TokenResultStatus.SHOULD_WAIT:
                // Wait for next tick.
                try {
                    //休眠指定时间再放行请求
                    Thread.sleep(result.getWaitInMs());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return true;
            case TokenResultStatus.NO_RULE_EXISTS:
            case TokenResultStatus.BAD_REQUEST:
            case TokenResultStatus.FAIL:
            case TokenResultStatus.TOO_MANY_REQUEST:
                //根据规则配置的fallbackToLocalWhenFail是否为true决定是否回退为本地限流，如果需要回退为本地限流，则调用passLocalCheck方法重新判断
                return fallbackToLocalOrPass(rule, context, node, acquireCount, prioritized);
            case TokenResultStatus.BLOCKED:
            default:
                //直接拒绝请求
                return false;
        }
    }
}