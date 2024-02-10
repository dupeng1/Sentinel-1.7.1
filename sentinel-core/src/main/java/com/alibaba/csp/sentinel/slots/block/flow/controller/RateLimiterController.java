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
package com.alibaba.csp.sentinel.slots.block.flow.controller;

import java.util.concurrent.atomic.AtomicLong;

import com.alibaba.csp.sentinel.slots.block.flow.TrafficShapingController;

import com.alibaba.csp.sentinel.util.TimeUtil;
import com.alibaba.csp.sentinel.node.Node;

/**
 * @author jialiang.linjl
 */

/**
 * 1、匀速限流效果控制器：Sentinel基于漏桶算法并结合虚拟队列等待机制实现了匀速限流效果。可将其理解为存在一个虚拟队列，使请求在队列中排队通过，
 * 每count/1000毫秒可通过一个请求
 * 2、匀速限流效果控制器适合用于请求突发性增长后剧降的场景，例如一个定时任务调用的接口，使用匀速限流效果控制器可以将突增的请求排队到低峰时执行，
 * 起到削峰填谷的效果
 *
 */
public class RateLimiterController implements TrafficShapingController {

    //请求在虚拟队列中的最大等待时间，默认为500毫秒
    private final int maxQueueingTimeMs;
    //限流阈值的QPS
    private final double count;
    //最近一个请求通过的时间，用于计算下一个请求的预期通过时间
    private final AtomicLong latestPassedTime = new AtomicLong(-1);

    public RateLimiterController(int timeOut, double count) {
        this.maxQueueingTimeMs = timeOut;
        this.count = count;
    }

    @Override
    public boolean canPass(Node node, int acquireCount) {
        return canPass(node, acquireCount, false);
    }

    /**
     * latestPassedTime永远存储当前请求的期望通过时间，后续的请求将排在该请求后面，这就是虚拟队列的核心实现，按期望通过时间排队。
     * 在虚拟队列中，将latestPassedTime回退一个时间间隔，相当于将虚拟队列中的一个元素移除
     * @param node 根据limitApp与strategy选出来的Node
     * @param acquireCount  与并发编程方法AQS#tryAcquire的参数作用一样，值一般为1；当限流规则配置的限流阈值类型为Threads时，表示需要申请一个线程；
     *                      当限流规则配置的限流阈值类型为QPS时，表示需要申请放行一个请求
     * @param prioritized   表示是否对请求进行优先级排序
     * @return
     */
    @Override
    public boolean canPass(Node node, int acquireCount, boolean prioritized) {
        // Pass when acquire count is less or equal than 0.
        if (acquireCount <= 0) {
            return true;
        }
        // Reject when count is less or equal than 0.
        // Otherwise,the costTime will be max of long and waitTime will overflow in some cases.
        if (count <= 0) {
            return false;
        }
        long currentTime = TimeUtil.currentTimeMillis();
        // Calculate the interval between every two requests.
        //1、计算队列中连续两个请求通过的时间间隔，如果限流阈值为200QPS，则costTime等于5，即每5毫秒只允许通过一个请求，而每5毫秒通过一个请求就是固定速率
        long costTime = Math.round(1.0 * (acquireCount) / count * 1000);
        //2、计算当前请求的期望通过时间，等于时间间隔加上最近一个请求通过的时间
        // Expected pass time of this request.
        long expectedTime = costTime + latestPassedTime.get();
        //3、如果期望时间小于或等于当前时间，则当前请求可立即通过
        if (expectedTime <= currentTime) {
            // Contention may exist here, but it's okay.
            latestPassedTime.set(currentTime);
            return true;
        } else {
            // Calculate the time to wait.
            //4、如果期望时间超过当前时间，则需要休眠等待，而需要等待的时间等于预期通过时间减去当前时间
            long waitTime = costTime + latestPassedTime.get() - TimeUtil.currentTimeMillis();
            //5、如果等待时间超过队列允许的最大等待时间，则会拒绝当前请求
            if (waitTime > maxQueueingTimeMs) {
                return false;
            } else {
                long oldTime = latestPassedTime.addAndGet(costTime);
                try {
                    //6、如果更新latestPassedTime为期望通过时间后，需要等待的时间还是少于等于最大等待时间，则说明排队有效
                    waitTime = oldTime - TimeUtil.currentTimeMillis();
                    if (waitTime > maxQueueingTimeMs) {
                        //否则说明在一瞬间被某个请求占位了，需要拒绝当前请求，将当前请求移出队列并回退一个时间间隔
                        latestPassedTime.addAndGet(-costTime);
                        return false;
                    }
                    // in race condition waitTime may <= 0
                    //7、休眠等待waitTime毫秒
                    if (waitTime > 0) {
                        Thread.sleep(waitTime);
                    }
                    return true;
                } catch (InterruptedException e) {
                }
            }
        }
        return false;
    }

}
