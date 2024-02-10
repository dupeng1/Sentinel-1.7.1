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
package com.alibaba.csp.sentinel.slotchain;

/**
 * Link all processor slots as a chain.
 *
 * @author qinan.qn
 */

/**
 * 1、处理器插槽链表，Sentinel按顺序将注册的处理器插槽构成有序的处理器插槽链表
 * 2、在执行方法之前根据ProcessorSlotChain调度处理器插槽完成资源指标数据的统计、限流、熔断降级等
 */
public abstract class ProcessorSlotChain extends AbstractLinkedProcessorSlot<Object> {

    /**
     * Add a processor to the head of this slot chain.
     *
     * @param protocolProcessor processor to be added.
     */
    //将一个ProcessorSlot添加到链表头节点
    public abstract void addFirst(AbstractLinkedProcessorSlot<?> protocolProcessor);

    /**
     * Add a processor to the tail of this slot chain.
     *
     * @param protocolProcessor processor to be added.
     */
    //将一个ProcessorSlot添加到链表末尾
    public abstract void addLast(AbstractLinkedProcessorSlot<?> protocolProcessor);
}
