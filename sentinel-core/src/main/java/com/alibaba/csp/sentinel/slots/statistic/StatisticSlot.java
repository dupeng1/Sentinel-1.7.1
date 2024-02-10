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
package com.alibaba.csp.sentinel.slots.statistic;

import java.util.Collection;

import com.alibaba.csp.sentinel.config.SentinelConfig;
import com.alibaba.csp.sentinel.slotchain.ProcessorSlotEntryCallback;
import com.alibaba.csp.sentinel.slotchain.ProcessorSlotExitCallback;
import com.alibaba.csp.sentinel.slots.block.flow.PriorityWaitException;
import com.alibaba.csp.sentinel.util.TimeUtil;
import com.alibaba.csp.sentinel.Constants;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.node.ClusterNode;
import com.alibaba.csp.sentinel.node.DefaultNode;
import com.alibaba.csp.sentinel.slotchain.AbstractLinkedProcessorSlot;
import com.alibaba.csp.sentinel.slotchain.ResourceWrapper;
import com.alibaba.csp.sentinel.slots.block.BlockException;

/**
 * <p>
 * A processor slot that dedicates to real time statistics.
 * When entering this slot, we need to separately count the following
 * information:
 * <ul>
 * <li>{@link ClusterNode}: total statistics of a cluster node of the resource ID.</li>
 * <li>Origin node: statistics of a cluster node from different callers/origins.</li>
 * <li>{@link DefaultNode}: statistics for specific resource name in the specific context.</li>
 * <li>Finally, the sum statistics of all entrances.</li>
 * </ul>
 * </p>
 *
 * @author jialiang.linjl
 * @author Eric Zhao
 */

/**
 * 1、真正用于实现资源指标数据统计的处理器插槽，它会先调用后续的ProcessorSlot类的entry方法判断是否放行请求，再根据结果执行相应的资源指标数据统计
 * 2、StatisticSlot是实现资源各项指标数据统计的处理器插槽，它与NodeSelectorSlot、ClusterBuilderSlot共同组成了资源指标数据统计流水线
 * 3、NodeSelectorSlot负责创建DefaultNode实例，并将DefaultNode实例向下传递给ClusterBuilderSlot
 * 4、ClusterBuilderSlot负责加工资源的DefaultNode实例，添加ClusterNode实例，然后将DefaultNode实例向下传递给StatisticSlot
 */
public class StatisticSlot extends AbstractLinkedProcessorSlot<DefaultNode> {

    /**
     * 先通过fireEntry方法调用后续的ProcessorSlot#entry方法，再根据后续的ProcessorSlot是否抛出BlockException来决定统计哪些指标数据，
     * 并将资源并行占用的线程数加1
     * @param context 当前调用链上下文
     * @param resourceWrapper   资源ID
     * @param node
     * @param count 表示申请占用共享资源的数量，只有申请到足够的共享资源时才能继续执行
     * @param prioritized   表示是否对请求进行优先级排序
     * @param args  调用方法传递的参数，用于实现热点参数限流
     * @throws Throwable
     */
    @Override
    public void entry(Context context, ResourceWrapper resourceWrapper, DefaultNode node, int count,
                      boolean prioritized, Object... args) throws Throwable {
        try {
            // Do some checking.
            fireEntry(context, resourceWrapper, node, count, prioritized, args);

            // Request passed, add thread count and pass count.
            //自增并行占用的线程数
            node.increaseThreadNum();
            //被放行请求总数加1
            node.addPassRequest(count);
            //如果调用来源不空，也将调用来源对应的StatisticNode当前并行占用线程数加1，当前时间窗口被放行请求数加1
            if (context.getCurEntry().getOriginNode() != null) {
                // Add count for origin node.
                //调用来源对应的StatisticNode自增并行占用的线程数
                context.getCurEntry().getOriginNode().increaseThreadNum();
                //调用来源对应的StatisticNode被放行请求数加1
                context.getCurEntry().getOriginNode().addPassRequest(count);
            }
            //如果流量类型为IN，则让统计整个应用所有流入类型流量的ENTRY_NODE自增并行占用线程数、当前时间窗口被放行的请求数加1
            if (resourceWrapper.getEntryType() == EntryType.IN) {
                // Add count for global inbound entry node for global statistics.
                //ENTRY_NODE自增并行占用的线程数
                Constants.ENTRY_NODE.increaseThreadNum();
                //ENTRY_NODE被放行请求数加1
                Constants.ENTRY_NODE.addPassRequest(count);
            }

            // Handle pass event with registered entry callback handlers.
            for (ProcessorSlotEntryCallback<DefaultNode> handler : StatisticSlotCallbackRegistry.getEntryCallbacks()) {
                handler.onPass(context, resourceWrapper, node, count, args);
            }
            } catch (PriorityWaitException ex) {
            //说明当前请求已经被休眠了一段时间了，但还是允许请求通过，只是不需要DefaultNode实例统计这个请求了，只自增当前资源并行占用的线程数
            //同时DefaultNode实例也会让ClusterNode实例自增并行占用的线程数
            node.increaseThreadNum();
            //当调用来源不为空时，让调用来源对应的StatisticNode自增并行占用的线程数
            if (context.getCurEntry().getOriginNode() != null) {
                // Add count for origin node.
                context.getCurEntry().getOriginNode().increaseThreadNum();
            }
            //当流量雷士为IN时，让ENTRY_NODE自增并行占用的线程数
            if (resourceWrapper.getEntryType() == EntryType.IN) {
                // Add count for global inbound entry node for global statistics.
                Constants.ENTRY_NODE.increaseThreadNum();
            }
            // Handle pass event with registered entry callback handlers.
            //回调所有的ProcessorSlotEntryCallback#onPass方法
            for (ProcessorSlotEntryCallback<DefaultNode> handler : StatisticSlotCallbackRegistry.getEntryCallbacks()) {
                handler.onPass(context, resourceWrapper, node, count, args);
            }
        } catch (BlockException e) {//只在需要拒绝请求时被抛出
            // Blocked, set block exception to current entry.
            //BlockException
            //将异常保存到调用链上下文的当前CtEntry实例中，StatisticSlot的exit方法会识别是统计请求异常指标还是统计请求被拒绝指标
            context.getCurEntry().setError(e);

            // Add block count.
            //自增请求被决绝总数，将当前时间窗口的block qps这项指标数据的值加1
            node.increaseBlockQps(count);
            if (context.getCurEntry().getOriginNode() != null) {
                //如果调用来源不为空，则让调用来源对应的StatisticSlot实例统计的请求被拒绝总数加1
                context.getCurEntry().getOriginNode().increaseBlockQps(count);
            }

            if (resourceWrapper.getEntryType() == EntryType.IN) {
                // Add count for global inbound entry node for global statistics.
                //如果流量类型是IN，则让ENTRY_NODE统计的请求被拒绝总数加1
                Constants.ENTRY_NODE.increaseBlockQps(count);
            }

            // Handle block event with registered entry callback handlers.
            //回调所有的ProcessorSlotEntryCallback#onBlocked方法
            for (ProcessorSlotEntryCallback<DefaultNode> handler : StatisticSlotCallbackRegistry.getEntryCallbacks()) {
                handler.onBlocked(e, context, resourceWrapper, node, count, args);
            }
            //捕获BlockException只是为了统计请求被拒绝的总数，而BlockException还是会被向上抛出，抛出的目的是拦住请求，执行服务降级处理
            throw e;
        } catch (Throwable e) {
            // Unexpected error, set error to current entry.
            //其他异常并非指业务异常，因为此时业务代码还未被执行，而业务代码抛出的异常，会通过调用Trace#trace方法统计请求异常总数
            //将异常保存到调用链上下文的当前 Entry 实例中
            context.getCurEntry().setError(e);

            // This should not happen.
            //让资源的DefaultNode实例自增当前时间窗口的请求异常总数
            node.increaseExceptionQps(count);
            if (context.getCurEntry().getOriginNode() != null) {
                //让调用来源的StatisticNode 实例自增当前时间窗口的请求异常总数
                context.getCurEntry().getOriginNode().increaseExceptionQps(count);
            }

            if (resourceWrapper.getEntryType() == EntryType.IN) {
                //如果流量类型为IN，则让ENTRY_NODE统计异常指标
                Constants.ENTRY_NODE.increaseExceptionQps(count);
            }
            throw e;
        }
    }

    /**
     * 1、若无任何异常，则统计请求成功、请求执行耗时指标，并将资源并行占用的线程数减1
     * 2、当exit方法被调用时，要么请求被拒绝，要么请求被放行且已经被执行完成，所以exit方法需要知道当前请求是否被正常执行完成，这正是StatisticSlot
     * 在捕获异常时将异常保存到当前CtEntry实例的原因，exit通过Context实例可以获取当前CtEntry实例，从当前CtEntry实例中可以获取entry方法中保存的异常
     * @param context         current {@link Context}
     * @param resourceWrapper current resource
     * @param count           tokens needed
     * @param args            parameters of the original call
     */
    @Override
    public void exit(Context context, ResourceWrapper resourceWrapper, int count, Object... args) {
        //1、通过Context实例可以获取当前资源的DefaultNode实例，如果entry方法中
        DefaultNode node = (DefaultNode)context.getCurNode();
        //如果entry方法中未出现异常，则说明请求是正常完成的
        if (context.getCurEntry().getError() == null) {
            // Calculate response time (max RT is statisticMaxRt from SentinelConfig).
            //2、当前计算耗时，可以将当前时间减去调用链上当前CtEntry实例的创建时间的值作为请求的执行耗时
            long rt = TimeUtil.currentTimeMillis() - context.getCurEntry().getCreateTime();
            int maxStatisticRt = SentinelConfig.statisticMaxRt();
            if (rt > maxStatisticRt) {
                rt = maxStatisticRt;
            }

            // Record response time and success count.
            //3、在请求被正常完成的情况下，需要统计总耗时指标，增加当前请求的执行耗时，统计成功请求总数，将成功请求总数加1
            node.addRtAndSuccess(rt, count);
            //4、如果调用来源不空，则让调用来源的StatisticSlot实例统计总耗时指标，增加当前请求执行耗时，统计成功请求总数，将成功请求总数加1
            if (context.getCurEntry().getOriginNode() != null) {
                context.getCurEntry().getOriginNode().addRtAndSuccess(rt, count);
            }
            //5、恢复当前资源占用的线程数
            node.decreaseThreadNum();
            //6、如果调用来源不空，则恢复当前调用来源占用的线程数
            if (context.getCurEntry().getOriginNode() != null) {
                context.getCurEntry().getOriginNode().decreaseThreadNum();
            }
            //7、如果流量类型为IN，则让ENTRY_NODE统计总耗时指标，增加当前请求的执行耗时，统计成功请求总数，将成功请求总数加1，恢复占用的线程数
            if (resourceWrapper.getEntryType() == EntryType.IN) {
                Constants.ENTRY_NODE.addRtAndSuccess(rt, count);
                Constants.ENTRY_NODE.decreaseThreadNum();
            }
        } else {
            // Error may happen.
        }

        // Handle exit event with registered exit callback handlers.
        //8、回调所有ProcessorSlotExitCallback#onExit方法
        Collection<ProcessorSlotExitCallback> exitCallbacks = StatisticSlotCallbackRegistry.getExitCallbacks();
        for (ProcessorSlotExitCallback handler : exitCallbacks) {
            handler.onExit(context, resourceWrapper, count, args);
        }

        fireExit(context, resourceWrapper, count);
    }
}
