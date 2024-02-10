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

import com.alibaba.csp.sentinel.node.Node;
import com.alibaba.csp.sentinel.node.OccupyTimeoutProperty;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.PriorityWaitException;
import com.alibaba.csp.sentinel.slots.block.flow.TrafficShapingController;
import com.alibaba.csp.sentinel.util.TimeUtil;

/**
 * Default throttling controller (immediately reject strategy).
 *
 * @author jialiang.linjl
 * @author Eric Zhao
 */

/**
 * 1、默认使用的流量效果控制器，实现的效果是直接拒绝超过阈值的请求，当QPS超过限流规则配置的阈值时，新的请求就会被立即拒绝，并抛出FlowException
 * 2、适用于明确知道系统处理能力的情况，如通过压测确定阈值
 */
public class DefaultController implements TrafficShapingController {

    private static final int DEFAULT_AVG_USED_TOKENS = 0;

    private double count;
    private int grade;

    public DefaultController(double count, int grade) {
        this.count = count;
        this.grade = grade;
    }

    @Override
    public boolean canPass(Node node, int acquireCount) {
        return canPass(node, acquireCount, false);
    }

    @Override
    public boolean canPass(Node node, int acquireCount, boolean prioritized) {
        //1、如果规则的限流阈值类型为QPS，则此方法返回Node统计的当前时间窗口已经放行的请求总数；
        // 如果规则的限流阈值类型为Threds，则此方法返回Node统计的当前并行占用的线程数
        int curCount = avgUsedTokens(node);
        //2、如果将当前请求放行会超过限流阈值且不满足条件3
        if (curCount + acquireCount > count) {
            //3、如果prioritized为true且规则的限流阈值类型为QPS，则表示具有优先级的请求可以占用未来时间窗口的统计指标
            //一般情况下，prioritized参数值为false，如果prioritized在传递过程中都没有修改过，则直接拒绝请求
            if (prioritized && grade == RuleConstant.FLOW_GRADE_QPS) {
                long currentTime;
                long waitInMs;
                currentTime = TimeUtil.currentTimeMillis();
                //4、如果可以占用未来时间窗口的统计指标，则tryOccupyNext会返回当前请求需要等待的时间，单位为毫秒
                waitInMs = node.tryOccupyNext(currentTime, acquireCount, count);
                //5、如果休眠时间在限制占用的最大时间范围内，则挂起当前请求，令当前线程休眠waitInMs毫秒
                //在休眠结束后抛出PriorityWaitException，标志当前请求是等待了waitInMs毫秒之后通过的
                if (waitInMs < OccupyTimeoutProperty.getOccupyTimeout()) {
                    //等待通过的请求总数加上acquireCount
                    node.addWaitingRequest(currentTime + waitInMs, acquireCount);
                    //占用未来的pass指标数量
                    node.addOccupiedPass(acquireCount);
                    //休眠等待，当前线程阻塞
                    sleep(waitInMs);

                    // PriorityWaitException indicates that the request will pass after waiting for {@link @waitInMs}.
                    //抛出PriorityWaitException，表示当前请求时等待了waitInMs之后通过的
                    throw new PriorityWaitException(waitInMs);
                }
            }
            return false;
        }
        return true;
    }

    private int avgUsedTokens(Node node) {
        if (node == null) {
            return DEFAULT_AVG_USED_TOKENS;
        }
        return grade == RuleConstant.FLOW_GRADE_THREAD ? node.curThreadNum() : (int)(node.passQps());
    }

    private void sleep(long timeMillis) {
        try {
            Thread.sleep(timeMillis);
        } catch (InterruptedException e) {
            // Ignore.
        }
    }
}
