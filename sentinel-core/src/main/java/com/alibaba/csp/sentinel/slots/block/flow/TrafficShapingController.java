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

import com.alibaba.csp.sentinel.node.Node;

/**
 * A universal interface for traffic shaping controller.
 *
 * @author jialiang.linjl
 */

/**
 * 限流算法用以控制通过的流量始终在限流阈值内，不同限流算法还可以控制流量到达的某种效果
 * 1、控制超阈值流量快速失败的【计数器算法】
 * 2、允许一定数量的请求等待通过的【漏斗算法】
 * 3、控制流量匀速通过且允许流量在一定成都上突增的【令牌桶算法】
 */
public interface TrafficShapingController {

    /**
     * Check whether given resource entry can pass with provided count.
     *
     * @param node resource node
     * @param acquireCount count to acquire
     * @param prioritized whether the request is prioritized
     * @return true if the resource entry can pass; false if it should be blocked
     */
    /**
     * 是否允许通过
     * @param node 根据limitApp与strategy选出来的Node
     * @param acquireCount  与并发编程方法AQS#tryAcquire的参数作用一样，值一般为1；当限流规则配置的限流阈值类型为Threads时，表示需要申请一个线程；
     *                      当限流规则配置的限流阈值类型为QPS时，表示需要申请放行一个请求
     * @param prioritized   表示是否对请求进行优先级排序
     * @return
     */
    boolean canPass(Node node, int acquireCount, boolean prioritized);

    /**
     * Check whether given resource entry can pass with provided count.
     *
     * @param node resource node
     * @param acquireCount count to acquire
     * @return true if the resource entry can pass; false if it should be blocked
     */
    boolean canPass(Node node, int acquireCount);
}
