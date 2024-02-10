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
package com.alibaba.csp.sentinel.slots.block.degrade;

import com.alibaba.csp.sentinel.concurrent.NamedThreadFactory;
import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.node.ClusterNode;
import com.alibaba.csp.sentinel.node.DefaultNode;
import com.alibaba.csp.sentinel.slots.block.AbstractRule;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.clusterbuilder.ClusterBuilderSlot;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * <p>
 * Degrade is used when the resources are in an unstable state, these resources
 * will be degraded within the next defined time window. There are two ways to
 * measure whether a resource is stable or not:
 * </p>
 * <ul>
 * <li>
 * Average response time ({@code DEGRADE_GRADE_RT}): When
 * the average RT exceeds the threshold ('count' in 'DegradeRule', in milliseconds), the
 * resource enters a quasi-degraded state. If the RT of next coming 5
 * requests still exceed this threshold, this resource will be downgraded, which
 * means that in the next time window (defined in 'timeWindow', in seconds) all the
 * access to this resource will be blocked.
 * </li>
 * <li>
 * Exception ratio: When the ratio of exception count per second and the
 * success qps exceeds the threshold, access to the resource will be blocked in
 * the coming window.
 * </li>
 * </ul>
 *
 * @author jialiang.linjl
 */
public class DegradeRule extends AbstractRule {

    @SuppressWarnings("PMD.ThreadPoolCreationRule")
    private static ScheduledExecutorService pool = Executors.newScheduledThreadPool(
        Runtime.getRuntime().availableProcessors(), new NamedThreadFactory("sentinel-degrade-reset-task", true));

    public DegradeRule() {}

    public DegradeRule(String resourceName) {
        setResource(resourceName);
    }

    /**
     * RT threshold or exception ratio threshold count.
     */
    //限流阈值，策略不同，代表不同阈值，如慢请求总数阈值、异常比率阈值、常总数阈值
    private double count;

    /**
     * Degrade recover timeout (in seconds) when degradation occurs.
     */
    //重置熔断的窗口时间，默认值为0
    private int timeWindow;

    /**
     * Degrade strategy (0: average RT, 1: exception ratio, 2: exception count).
     */
    //熔断降级策略，支持按平均响应耗时、按失败比率、按失败次数
    private int grade = RuleConstant.DEGRADE_GRADE_RT;

    /**
     * Minimum number of consecutive slow requests that can trigger RT circuit breaking.
     *
     * @since 1.7.0
     */
    //当熔断降级策略被配置为按平均响应耗时，该值表示触发熔断超过限流阈值的慢请求数量
    //当有连续rtSlowRequestAmount个请求的计算平均响应耗时都超过count时，后面的请求才会被熔断，且在下个时间窗口恢复
    private int rtSlowRequestAmount = RuleConstant.DEGRADE_DEFAULT_SLOW_REQUEST_AMOUNT;

    /**
     * Minimum number of requests (in an active statistic time span) that can trigger circuit breaking.
     *
     * @since 1.7.0
     */
    //当熔断降级策略被配置为按失败比率，该值表示触发熔断的最小请求数
    //在第一个请求就失败的情况下，失败率为100%，而minRequestAmount可以避免这种情况的出现
    private int minRequestAmount = RuleConstant.DEGRADE_DEFAULT_MIN_REQUEST_AMOUNT;

    public int getGrade() {
        return grade;
    }

    public DegradeRule setGrade(int grade) {
        this.grade = grade;
        return this;
    }

    public double getCount() {
        return count;
    }

    public DegradeRule setCount(double count) {
        this.count = count;
        return this;
    }

    public int getTimeWindow() {
        return timeWindow;
    }

    public DegradeRule setTimeWindow(int timeWindow) {
        this.timeWindow = timeWindow;
        return this;
    }

    public int getRtSlowRequestAmount() {
        return rtSlowRequestAmount;
    }

    public DegradeRule setRtSlowRequestAmount(int rtSlowRequestAmount) {
        this.rtSlowRequestAmount = rtSlowRequestAmount;
        return this;
    }

    public int getMinRequestAmount() {
        return minRequestAmount;
    }

    public DegradeRule setMinRequestAmount(int minRequestAmount) {
        this.minRequestAmount = minRequestAmount;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        if (!super.equals(o)) { return false; }
        DegradeRule that = (DegradeRule) o;
        return Double.compare(that.count, count) == 0 &&
            timeWindow == that.timeWindow &&
            grade == that.grade &&
            rtSlowRequestAmount == that.rtSlowRequestAmount &&
            minRequestAmount == that.minRequestAmount;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + new Double(count).hashCode();
        result = 31 * result + timeWindow;
        result = 31 * result + grade;
        result = 31 * result + rtSlowRequestAmount;
        result = 31 * result + minRequestAmount;
        return result;
    }

    @Override
    public String toString() {
        return "DegradeRule{" +
            "resource=" + getResource() +
            ", grade=" + grade +
            ", count=" + count +
            ", limitApp=" + getLimitApp() +
            ", timeWindow=" + timeWindow +
            ", rtSlowRequestAmount=" + rtSlowRequestAmount +
            ", minRequestAmount=" + minRequestAmount +
            "}";
    }

    // Internal implementation (will be deprecated and moved outside).

    //当熔断降级策略被配置为按平均响应耗时，用于累加慢请求数
    private AtomicLong passCount = new AtomicLong(0);
    //用于记录当前是否已经触发熔断
    private final AtomicBoolean cut = new AtomicBoolean(false);

    @Override
    public boolean passCheck(Context context, DefaultNode node, int acquireCount, Object... args) {
        if (cut.get()) {
            return false;
        }
        //1、根据资源名称获取统计该资源全局指标数据的ClusterNode
        ClusterNode clusterNode = ClusterBuilderSlot.getClusterNode(this.getResource());
        if (clusterNode == null) {
            return true;
        }
        //2、如果熔断降级策略为DEGRADE_GRADE_RT，则从ClusterNode实例中读取当前平均响应耗时，如果平均响应耗时小于阈值，则将慢请求计数器passCount
        //重置为0，放行请求；若平均响应耗时超过阈值，并且超过阈值的慢请求数累计值已经大于rtSlowRequestAmount的值，则熔断当前请求
        if (grade == RuleConstant.DEGRADE_GRADE_RT) {
            double rt = clusterNode.avgRt();
            if (rt < this.count) {
                passCount.set(0);
                return true;
            }

            // Sentinel will degrade the service only if count exceeds.
            if (passCount.incrementAndGet() < rtSlowRequestAmount) {
                return true;
            }
        }
        //3、如果熔断降级策略为DEGRADE_GRADE_EXCEPTION_RATIO，则从ClusterNode实例中读取当前时间窗口的异常总数、成功总数、总请求数，
        //若异常总数和成功总数的比值小于熔断降级规则配置的阈值则放行请求；若异常总数与成功总数的比值大于或等于阈值，并且当前总请求数大于
        //minRequestAmount，则熔断当前请求
        else if (grade == RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO) {
            double exception = clusterNode.exceptionQps();
            double success = clusterNode.successQps();
            double total = clusterNode.totalQps();
            // If total amount is less than minRequestAmount, the request will pass.
            //总请求数小于minRequestAmount直接放行当前请求
            if (total < minRequestAmount) {
                return true;
            }

            // In the same aligned statistic time window,
            // "success" (aka. completed count) = exception count + non-exception count (realSuccess)
            double realSuccess = success - exception;
            if (realSuccess <= 0 && exception < minRequestAmount) {
                return true;
            }

            if (exception / success < count) {
                return true;
            }
        }
        //4、如果熔断降级策略为DEGRADE_GRADE_EXCEPTION_COUNT，读取当前滑动窗口的异常总数，如果异常总数小于熔断降级规则配置的阈值，则放行请求
        //由于统计时间窗口时分钟级别的，若timeWindow小于60s，则结束熔断状态后仍可能再次进入熔断状态，因为调用ClusterNode实例的totalException
        //方法获取的是1分钟内的异常总数
        else if (grade == RuleConstant.DEGRADE_GRADE_EXCEPTION_COUNT) {
            double exception = clusterNode.totalException();
            if (exception < count) {
                return true;
            }
        }
        //5、更改熔断标志为true，使后续请求不需要重复判断，并且开启定时任务，用于重置熔断标志，在休眠timeWindow时长后重置熔断标志
        if (cut.compareAndSet(false, true)) {
            ResetTask resetTask = new ResetTask(this);
            pool.schedule(resetTask, timeWindow, TimeUnit.SECONDS);
        }
        //熔断
        return false;
    }

    private static final class ResetTask implements Runnable {

        private DegradeRule rule;

        ResetTask(DegradeRule rule) {
            this.rule = rule;
        }

        @Override
        public void run() {
            rule.passCount.set(0);
            rule.cut.set(false);
        }
    }
}
