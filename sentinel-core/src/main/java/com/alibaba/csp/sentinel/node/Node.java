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
package com.alibaba.csp.sentinel.node;

import java.util.List;
import java.util.Map;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.node.metric.MetricNode;
import com.alibaba.csp.sentinel.slots.statistic.metric.DebugSupport;
import com.alibaba.csp.sentinel.util.function.Predicate;

/**
 * Holds real-time statistics for resources.
 *
 * @author qinan.qn
 * @author leyou
 * @author Eric Zhao
 */

/**
 * 定义统计资源实时指标数据的方法，可以对外屏蔽滑动窗口的存在
 * Node接口的不同实现类被用在不同维度为资源统计实时的指标数据，如区分不同调用链、区分不同调用来源
 */
public interface Node extends OccupySupport, DebugSupport {

    /**
     * Get incoming request per minute ({@code pass + block}).
     *
     * @return total request count per minute
     */
    //获取请求总数
    long totalRequest();

    /**
     * Get pass count per minute.
     *
     * @return total passed request count per minute
     * @since 1.5.0
     */
    //获取被放行的请求总数
    long totalPass();

    /**
     * Get {@link Entry#exit()} count per minute.
     *
     * @return total completed request count per minute
     */
    //获取响应成功的请求总数，即被放行且未出现异常的请求总数
    long totalSuccess();

    /**
     * Get blocked request count per minute (totalBlockRequest).
     *
     * @return total blocked request count per minute
     */
    //获取被拒绝的请求总数
    long blockRequest();

    /**
     * Get exception count per minute.
     *
     * @return total business exception count per minute
     */
    //获取发生异常的请求总数
    long totalException();

    /**
     * Get pass request per second.
     *
     * @return QPS of passed requests
     */
    //获取当前时间窗口被放行的请求总数
    double passQps();

    /**
     * Get block request per second.
     *
     * @return QPS of blocked requests
     */
    //获取当前时间窗口被拒绝的请求总数
    double blockQps();

    /**
     * Get {@link #passQps()} + {@link #blockQps()} request per second.
     *
     * @return QPS of passed and blocked requests
     */
    //获取当前时间窗口的请求总数
    double totalQps();

    /**
     * Get {@link Entry#exit()} request per second.
     *
     * @return QPS of completed requests
     */
    //获取当前时间窗口响应成功的请求总数
    double successQps();

    /**
     * Get estimated max success QPS till now.
     *
     * @return max completed QPS
     */
    //获取一段时间内最大的 successQps，例如，若秒级滑动窗口的数组大
    //小的默认配置为 2，则获取数组中 successQps 值最大的一个
    double maxSuccessQps();

    /**
     * Get exception count per second.
     *
     * @return QPS of exception occurs
     */
    //获取当前时间窗口发生异常的请求总数
    double exceptionQps();

    /**
     * Get average rt per second.
     *
     * @return average response time per second
     */
    //获取平均耗时
    double avgRt();

    /**
     * Get minimal response time.
     *
     * @return recorded minimal response time
     */
    //获取最小耗时
    double minRt();

    /**
     * Get current active thread count.
     *
     * @return current active thread count
     */
    //获取当前并行占用的线程数
    int curThreadNum();

    /**
     * Get last second block QPS.
     */
    //获取前一个时间窗口的 blockQps
    double previousBlockQps();

    /**
     * Last window QPS.
     */
    //获取前一个时间窗口的 passQps
    double previousPassQps();

    /**
     * Fetch all valid metric nodes of resources.
     *
     * @return valid metric nodes of resources
     */
    Map<Long, MetricNode> metrics();

    /**
     * Fetch all raw metric items that satisfies the time predicate.
     *
     * @param timePredicate time predicate
     * @return raw metric items that satisfies the time predicate
     * @since 1.7.0
     */
    List<MetricNode> rawMetricsInMin(Predicate<Long> timePredicate);

    /**
     * Add pass count.
     *
     * @param count count to add pass
     */
    //当前时间窗口被放行的请求总数+count
    void addPassRequest(int count);

    /**
     * Add rt and success count.
     *
     * @param rt      response time
     * @param success success count to add
     */
    //当前时间窗口响应成功的请求总数+success 及总耗时+rt
    void addRtAndSuccess(long rt, int success);

    /**
     * Increase the block count.
     *
     * @param count count to add
     */
    //当前时间窗口被拒绝的请求总数+1
    void increaseBlockQps(int count);

    /**
     * Add the biz exception count.
     *
     * @param count count to add
     */
    //当前时间窗口发生异常的请求总数+1
    void increaseExceptionQps(int count);

    /**
     * Increase current thread count.
     */
    //并行占用线程数+1
    void increaseThreadNum();

    /**
     * Decrease current thread count.
     */
    //并行占用线程数-1
    void decreaseThreadNum();

    /**
     * Reset the internal counter. Reset is needed when {@link IntervalProperty#INTERVAL} or
     * {@link SampleCountProperty#SAMPLE_COUNT} is changed.
     */
    void reset();
}
