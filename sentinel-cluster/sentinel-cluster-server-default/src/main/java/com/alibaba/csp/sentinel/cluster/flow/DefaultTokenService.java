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
package com.alibaba.csp.sentinel.cluster.flow;

import java.util.Collection;

import com.alibaba.csp.sentinel.cluster.TokenResultStatus;
import com.alibaba.csp.sentinel.cluster.TokenResult;
import com.alibaba.csp.sentinel.cluster.TokenService;
import com.alibaba.csp.sentinel.cluster.flow.rule.ClusterFlowRuleManager;
import com.alibaba.csp.sentinel.cluster.flow.rule.ClusterParamFlowRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;

/**
 * Default implementation for cluster {@link TokenService}.
 *
 * @author Eric Zhao
 * @since 1.4.0
 */

/**
 * 当使用独立应用模式启动集群限流服务端时，使用的是DefaultTokenService
 * 无论是集群限流服务端接收集群限流客户端发来的requestToken请求，还是在嵌入式模式下自己向自己发起请求，最终都会交给DefaultTokenService处理
 */
public class DefaultTokenService implements TokenService {

    @Override
    public TokenResult requestToken(Long ruleId, int acquireCount, boolean prioritized) {
        //验证规则是否存在，只使用一个ID字段向集群限流服务端传递限流规则，减小了数据包的大小，优化了网络通信的性能
        if (notValidRequest(ruleId, acquireCount)) {
            return badRequest();
        }
        // The rule should be valid.
        //1、根据集群限流规则ID获取限流规则
        FlowRule rule = ClusterFlowRuleManager.getFlowRuleById(ruleId);
        if (rule == null) {
            return new TokenResult(TokenResultStatus.NO_RULE_EXISTS);
        }
        //2、调用ClusterFlowChecker#acquireClusterToken方法继续处理请求
        return ClusterFlowChecker.acquireClusterToken(rule, acquireCount, prioritized);
    }

    @Override
    public TokenResult requestParamToken(Long ruleId, int acquireCount, Collection<Object> params) {
        if (notValidRequest(ruleId, acquireCount) || params == null || params.isEmpty()) {
            return badRequest();
        }
        // The rule should be valid.
        ParamFlowRule rule = ClusterParamFlowRuleManager.getParamRuleById(ruleId);
        if (rule == null) {
            return new TokenResult(TokenResultStatus.NO_RULE_EXISTS);
        }

        return ClusterParamFlowChecker.acquireClusterToken(rule, acquireCount, params);
    }

    private boolean notValidRequest(Long id, int count) {
        return id == null || id <= 0 || count <= 0;
    }

    private TokenResult badRequest() {
        return new TokenResult(TokenResultStatus.BAD_REQUEST);
    }
}
