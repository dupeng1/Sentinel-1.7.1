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
package com.alibaba.csp.sentinel.slots.system;

import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.node.DefaultNode;
import com.alibaba.csp.sentinel.slotchain.AbstractLinkedProcessorSlot;
import com.alibaba.csp.sentinel.slotchain.ProcessorSlot;
import com.alibaba.csp.sentinel.slotchain.ResourceWrapper;

/**
 * A {@link ProcessorSlot} that dedicates to {@link SystemRule} checking.
 *
 * @author jialiang.linjl
 * @author leyou
 */

/**
 * 1、实现系统自适应限流，系统自适应是指系统能够在负载过高的情况下自动拒绝请求，在保证服务稳定运行的情况下尽量处理更多请求，一旦系统负载达到阈值，就拒绝请求
 * 2、被放在FlowSlot前面，强制系统优先考虑目前的情况能否处理当前请求，是系统尽量在实现最大吞吐量的同时保证稳定性
 */
public class SystemSlot extends AbstractLinkedProcessorSlot<DefaultNode> {

    @Override
    public void entry(Context context, ResourceWrapper resourceWrapper, DefaultNode node, int count,
                      boolean prioritized, Object... args) throws Throwable {
        SystemRuleManager.checkSystem(resourceWrapper);
        fireEntry(context, resourceWrapper, node, count, prioritized, args);
    }

    @Override
    public void exit(Context context, ResourceWrapper resourceWrapper, int count, Object... args) {
        fireExit(context, resourceWrapper, count, args);
    }

}
