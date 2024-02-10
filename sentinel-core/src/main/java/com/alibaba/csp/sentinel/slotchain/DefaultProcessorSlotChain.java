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

import com.alibaba.csp.sentinel.context.Context;

/**
 * @author qinan.qn
 * @author jialiang.linjl
 */

/**
 * Sentinel会为每个资源创建且仅创建一个ProcessorSlotChain实例，ProcessorSlotChain实例被缓存在CtSph类的chainMap静态字段中，key
 * 为资源ID，每个资源的ProcessorSlotChain实例在CtSph#entryWithPriority方法中被创建
 */
public class DefaultProcessorSlotChain extends ProcessorSlotChain {

    //指向链表头节点
    AbstractLinkedProcessorSlot<?> first = new AbstractLinkedProcessorSlot<Object>() {

        @Override
        public void entry(Context context, ResourceWrapper resourceWrapper, Object t, int count, boolean prioritized, Object... args)
            throws Throwable {
            super.fireEntry(context, resourceWrapper, t, count, prioritized, args);
        }

        @Override
        public void exit(Context context, ResourceWrapper resourceWrapper, int count, Object... args) {
            super.fireExit(context, resourceWrapper, count, args);
        }

    };
    //指向链表尾节点
    AbstractLinkedProcessorSlot<?> end = first;

    //在链表头部添加一个节点
    @Override
    public void addFirst(AbstractLinkedProcessorSlot<?> protocolProcessor) {
        protocolProcessor.setNext(first.getNext());
        first.setNext(protocolProcessor);
        if (end == first) {
            end = protocolProcessor;
        }
    }

    //在链表尾部添加一个节点
    @Override
    public void addLast(AbstractLinkedProcessorSlot<?> protocolProcessor) {
        end.setNext(protocolProcessor);
        end = protocolProcessor;
    }

    /**
     * Same as {@link #addLast(AbstractLinkedProcessorSlot)}.
     *
     * @param next processor to be added.
     */
    @Override
    public void setNext(AbstractLinkedProcessorSlot<?> next) {
        addLast(next);
    }

    @Override
    public AbstractLinkedProcessorSlot<?> getNext() {
        return first.getNext();
    }

    //调用链表头节点的entry方法，由头节点调用下一节点的entry方法
    //ProcessorSlot实例的entry方法是按ProcessorSlot实例在链表中的顺序被调用的
    @Override
    public void entry(Context context, ResourceWrapper resourceWrapper, Object t, int count, boolean prioritized, Object... args)
        throws Throwable {
        first.transformEntry(context, resourceWrapper, t, count, prioritized, args);
    }

    //调用链表尾头节点的exit方法，由头节点调用下一节点的exit方法
    //ProcessorSlot实例的exit方法被调用的顺序是从后往前走的
    @Override
    public void exit(Context context, ResourceWrapper resourceWrapper, int count, Object... args) {
        first.exit(context, resourceWrapper, count, args);
    }

}
