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
 * A container of some process and ways of notification when the process is finished.
 *
 * @author qinan.qn
 * @author jialiang.linjl
 * @author leyou(lihao)
 * @author Eric Zhao
 */

/**
 * 1、处理器插槽，资源指标数据的统计、限流、熔断、降级、系统自适应保护等都需要通过处理器插槽实现，
 * 2、一类是负责资源指标数据统计的 ProcessorSlot，可以在处理请求之前和完成请求处理之后进行各种资源指标数据的统计，
 * 3、一类是实现限流、熔断等流量控制功能的 ProcessorSlot，可以在处理请求之前决定是否放行请求(通过抛出异常来拒绝请求)
 * 并控制请求的实际通过时间(将线程休眠)<br>
 * 4、Sentinel使用责任链模式将注册的所有 ProcessorSlot 按照一定的顺序串成一个单向链表，实现资源指标数据统计的ProcessorSlot必须在
 * 实现流量控制功能的ProcessorSlot的前面，原因是限流、熔断降级等都需要依赖资源的实时指标数据做判断。同一分类下的 ProcessorSlot
 * 可能也需要有严格的排序，如完成资源指标数据统计的ProcessorSlot的排序为NodeSelectorSlot、ClusterBuilderSlot、StatisticSlot。
 * @param <T>
 */
public interface ProcessorSlot<T> {

    /**
     * Entrance of this slot.
     *
     * @param context         current {@link Context}
     * @param resourceWrapper current resource
     * @param param           generics parameter, usually is a {@link com.alibaba.csp.sentinel.node.Node}
     * @param count           tokens needed
     * @param prioritized     whether the entry is prioritized
     * @param args            parameters of the original call
     * @throws Throwable blocked exception or unexpected error
     */
    /**
     * 入口方法
     * @param context 当前调用链上下文
     * @param resourceWrapper   资源ID
     * @param param 泛型参数，一般用于传递资源的DefaultNode实例
     * @param count 表示申请占用共享资源的数量，只有申请到足够的共享资源时才能继续执行
     * @param prioritized   表示是否对请求进行优先级排序
     * @param args  调用方法传递的参数，用于实现热点参数限流
     * @throws Throwable
     */
    void entry(Context context, ResourceWrapper resourceWrapper, T param, int count, boolean prioritized,
               Object... args) throws Throwable;

    /**
     * Means finish of {@link #entry(Context, ResourceWrapper, Object, int, boolean, Object...)}.
     *
     * @param context         current {@link Context}
     * @param resourceWrapper current resource
     * @param obj             relevant object (e.g. Node)
     * @param count           tokens needed
     * @param prioritized     whether the entry is prioritized
     * @param args            parameters of the original call
     * @throws Throwable blocked exception or unexpected error
     */
    //调用下一个 ProcessorSlot 的 entry 方法
    void fireEntry(Context context, ResourceWrapper resourceWrapper, Object obj, int count, boolean prioritized,
                   Object... args) throws Throwable;

    /**
     * Exit of this slot.
     *
     * @param context         current {@link Context}
     * @param resourceWrapper current resource
     * @param count           tokens needed
     * @param args            parameters of the original call
     */
    //出口方法
    void exit(Context context, ResourceWrapper resourceWrapper, int count, Object... args);

    /**
     * Means finish of {@link #exit(Context, ResourceWrapper, int, Object...)}.
     *
     * @param context         current {@link Context}
     * @param resourceWrapper current resource
     * @param count           tokens needed
     * @param args            parameters of the original call
     */
    //调用下一个 ProcessorSlot 的 exit 方法
    void fireExit(Context context, ResourceWrapper resourceWrapper, int count, Object... args);
}
