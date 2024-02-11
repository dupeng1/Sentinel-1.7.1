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
package com.alibaba.csp.sentinel.slots.block.flow.param;

import java.util.List;

import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.node.DefaultNode;
import com.alibaba.csp.sentinel.slotchain.AbstractLinkedProcessorSlot;
import com.alibaba.csp.sentinel.slotchain.ResourceWrapper;
import com.alibaba.csp.sentinel.slots.block.BlockException;

/**
 * A processor slot that is responsible for flow control by frequent ("hot spot") parameters.
 *
 * @author jialiang.linjl
 * @author Eric Zhao
 * @since 0.2.0
 */
public class ParamFlowSlot extends AbstractLinkedProcessorSlot<DefaultNode> {

    @Override
    public void entry(Context context, ResourceWrapper resourceWrapper, DefaultNode node, int count,
                      boolean prioritized, Object... args) throws Throwable {
        //判断当前资源是否存在参数限流规则
        if (!ParamFlowRuleManager.hasRules(resourceWrapper.getName())) {
            fireEntry(context, resourceWrapper, node, count, prioritized, args);
            return;
        }
        //如果存在，则调用checkFlow方法判断当前请求是否允许通过
        checkFlow(resourceWrapper, count, args);
        fireEntry(context, resourceWrapper, node, count, prioritized, args);
    }

    @Override
    public void exit(Context context, ResourceWrapper resourceWrapper, int count, Object... args) {
        fireExit(context, resourceWrapper, count, args);
    }

    void applyRealParamIdx(/*@NonNull*/ ParamFlowRule rule, int length) {
        int paramIdx = rule.getParamIdx();
        if (paramIdx < 0) {
            if (-paramIdx <= length) {
                rule.setParamIdx(length + paramIdx);
            } else {
                // Illegal index, give it a illegal positive value, latter rule checking will pass.
                rule.setParamIdx(-paramIdx);
            }
        }
    }

    void checkFlow(ResourceWrapper resourceWrapper, int count, Object... args) throws BlockException {
        //1、方法的最后一个参数是请求参数，也就是调用SphU#entry方法传递进来的参数，如果参数为null，则没有必要走后续逻辑
        if (args == null) {
            return;
        }

        if (!ParamFlowRuleManager.hasRules(resourceWrapper.getName())) {
            return;
        }
        //2、调用 ParamFlowRuleManager#getRulesOfResource方法获取当前资源配置的所有参数限流规则
        List<ParamFlowRule> rules = ParamFlowRuleManager.getRulesOfResource(resourceWrapper.getName());
        //3、遍历参数限流规则
        for (ParamFlowRule rule : rules) {
            applyRealParamIdx(rule, args.length);

            // Initialize the parameter metrics.
            //判断是否需要为当前资源初始化创建ParameterMetric实例
            ParameterMetricStorage.initParamMetricsFor(resourceWrapper, rule);
            //判断当前请求是否可以被放行，如果需要拒绝请求，则抛出ParamFlowException
            if (!ParamFlowChecker.passCheck(resourceWrapper, rule, count, args)) {
                String triggeredParam = "";
                if (args.length > rule.getParamIdx()) {
                    Object value = args[rule.getParamIdx()];
                    triggeredParam = String.valueOf(value);
                }
                throw new ParamFlowException(resourceWrapper.getName(), triggeredParam, rule);
            }
        }
    }
}
