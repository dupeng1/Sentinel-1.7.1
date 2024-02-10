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
package com.alibaba.csp.sentinel;

import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.context.ContextUtil;
import com.alibaba.csp.sentinel.context.NullContext;
import com.alibaba.csp.sentinel.node.Node;
import com.alibaba.csp.sentinel.slotchain.ProcessorSlot;
import com.alibaba.csp.sentinel.slotchain.ResourceWrapper;

/**
 * Linked entry within current context.
 *
 * @author jialiang.linjl
 * @author Eric Zhao
 */

/**
 * 1、在处理一次请求的过程中，Sentinel会为调用链上的每个资源都创建一个CtEntry实例，每个CtEntry实例引用资源对应的ProcessorSlotChain。
 * 如果是首次访问资源，则需要为资源创建ProcessorSlotChain，注册在ProcessorSlotChain上的每个ProcessorSlot都是一个流量切入点
 * 2、用于维护父子Entry关系，每一次调用SphU类的entry方法都会创建一个CtEntry实例。
 * 3、如果调用链上多次调用SphU类的entry方法（调用链上有多个资源），那么调用链上这些资源的CtEntry实例会构成一个双向链表。
 * 每次创建CtEntry实例都会将Context实例的curEntry字段设置为这个新的CtEntry实例，双向链表的作用就是在调用CtEntry类
 * 的exit方法时，能够将Context实例的curEntry字段还原为引用调用链上前一个curEntry实例
 */
class CtEntry extends Entry {
    //当前 Entry 实例指向的父 Entry
    protected Entry parent = null;
    //当前 Entry 实例指向的下一个 Entry 实例
    protected Entry child = null;
    //当前资源的 ProcessorSlotChain 实例，Sentinel 会为每个资源创建且仅创建一个
    //ProcessorSlotChain 实例
    protected ProcessorSlot<Object> chain;
    //调用链上的Context实例
    protected Context context;

    CtEntry(ResourceWrapper resourceWrapper, ProcessorSlot<Object> chain, Context context) {
        super(resourceWrapper);
        this.chain = chain;
        this.context = context;

        setUpEntryFor(context);
    }

    private void setUpEntryFor(Context context) {
        // The entry should not be associated to NullContext.
        if (context instanceof NullContext) {
            return;
        }
        this.parent = context.getCurEntry();
        if (parent != null) {
            ((CtEntry)parent).child = this;
        }
        context.setCurEntry(this);
    }

    @Override
    public void exit(int count, Object... args) throws ErrorEntryFreeException {
        trueExit(count, args);
    }

    protected void exitForContext(Context context, int count, Object... args) throws ErrorEntryFreeException {
        if (context != null) {
            // Null context should exit without clean-up.
            if (context instanceof NullContext) {
                return;
            }
            if (context.getCurEntry() != this) {
                String curEntryNameInContext = context.getCurEntry() == null ? null : context.getCurEntry().getResourceWrapper().getName();
                // Clean previous call stack.
                CtEntry e = (CtEntry)context.getCurEntry();
                while (e != null) {
                    e.exit(count, args);
                    e = (CtEntry)e.parent;
                }
                String errorMessage = String.format("The order of entry exit can't be paired with the order of entry"
                    + ", current entry in context: <%s>, but expected: <%s>", curEntryNameInContext, resourceWrapper.getName());
                throw new ErrorEntryFreeException(errorMessage);
            } else {
                //调用资源的ProcessorSlotChain的exit方法，完成一次单向链表的exit方法
                if (chain != null) {
                    chain.exit(context, resourceWrapper, count, args);
                }
                // Restore the call stack.
                /**
                 * 1、移除CtEntry链表的尾节点，将Context的curEntry字段回退为当前CtEntry的父节点。
                 * 2、CtEntry用于维护父子Entry，每调用一次SphU#entry方法都会创建一个CtEntry实例，
                 * 如果在应用处理一次请求的路径上多次执行SphU#entry方法，那么这些CtEntry实例会
                 * 构成一个双向链表。
                 * 3、在每次创建CtEntry实例时，都会将Context实例的curEntry字段指向这个新的CtEntry实例，
                 * 双向链表的作用就是在调用CtEntry实例的exit方法时，能够将Context实例的curEntry字段指向上一个CtEntry实例
                 */
                context.setCurEntry(parent);
                if (parent != null) {
                    ((CtEntry)parent).child = null;
                }
                if (parent == null) {
                    // Default context (auto entered) will be exited automatically.
                    if (ContextUtil.isDefaultContext(context)) {
                        ContextUtil.exit();
                    }
                }
                // Clean the reference of context in current entry to avoid duplicate exit.
                clearEntryContext();
            }
        }
    }

    protected void clearEntryContext() {
        this.context = null;
    }

    @Override
    protected Entry trueExit(int count, Object... args) throws ErrorEntryFreeException {
        exitForContext(context, count, args);

        return parent;
    }

    @Override
    public Node getLastNode() {
        return parent == null ? null : parent.getCurNode();
    }
}