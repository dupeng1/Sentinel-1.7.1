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
package com.alibaba.csp.sentinel.slots.block.flow.param;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.alibaba.csp.sentinel.cluster.ClusterStateManager;
import com.alibaba.csp.sentinel.cluster.TokenResult;
import com.alibaba.csp.sentinel.cluster.TokenResultStatus;
import com.alibaba.csp.sentinel.cluster.TokenService;
import com.alibaba.csp.sentinel.cluster.client.TokenClientProvider;
import com.alibaba.csp.sentinel.cluster.server.EmbeddedClusterTokenServerProvider;
import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.slotchain.ResourceWrapper;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.statistic.cache.CacheMap;
import com.alibaba.csp.sentinel.util.TimeUtil;

/**
 * Rule checker for parameter flow control.
 *
 * @author Eric Zhao
 * @since 0.2.0
 */

/**
 * 控制每个时间窗口只生产一次令牌，将令牌放入令牌桶中，每个请求都从令牌桶中取走令牌，当令牌组后时放行请求，当令牌不足时拒绝请求
 */
public final class ParamFlowChecker {

    //返回true表示放行，返回false表示拒绝
    public static boolean passCheck(ResourceWrapper resourceWrapper, /*@Valid*/ ParamFlowRule rule, /*@Valid*/ int count,
                             Object... args) {
        //1、若参数为空，或者规则配置的参数索引越界，或者参数索引对应的参数值为空，则放行请求
        if (args == null) {
            return true;
        }

        int paramIdx = rule.getParamIdx();
        if (args.length <= paramIdx) {
            return true;
        }

        // Get parameter value. If value is null, then pass.
        Object value = args[paramIdx];
        if (value == null) {
            return true;
        }
        //2、若是集群限流模式，则调用passClusterCheck方法，否则调用passLocalCheck方法
        if (rule.isClusterMode() && rule.getGrade() == RuleConstant.FLOW_GRADE_QPS) {
            return passClusterCheck(resourceWrapper, rule, count, value);
        }

        return passLocalCheck(resourceWrapper, rule, count, value);
    }

    private static boolean passLocalCheck(ResourceWrapper resourceWrapper, ParamFlowRule rule, int count,
                                          Object value) {
        try {
            //基本数据类型
            if (Collection.class.isAssignableFrom(value.getClass())) {
                for (Object param : ((Collection)value)) {
                    if (!passSingleValueCheck(resourceWrapper, rule, count, param)) {
                        return false;
                    }
                }
            }
            //数组类型
            else if (value.getClass().isArray()) {
                int length = Array.getLength(value);
                for (int i = 0; i < length; i++) {
                    Object param = Array.get(value, i);
                    if (!passSingleValueCheck(resourceWrapper, rule, count, param)) {
                        return false;
                    }
                }
            }
            //引用类型
            else {
                return passSingleValueCheck(resourceWrapper, rule, count, value);
            }
        } catch (Throwable e) {
            RecordLog.warn("[ParamFlowChecker] Unexpected error", e);
        }

        return true;
    }

    static boolean passSingleValueCheck(ResourceWrapper resourceWrapper, ParamFlowRule rule, int acquireCount,
                                        Object value) {
        //1、当规则配置的阈值类型为QPS，根据限流效果调用passThrottleLocalCheck方法或passDefaultLocalCheck方法
        if (rule.getGrade() == RuleConstant.FLOW_GRADE_QPS) {
            if (rule.getControlBehavior() == RuleConstant.CONTROL_BEHAVIOR_RATE_LIMITER) {
                return passThrottleLocalCheck(resourceWrapper, rule, acquireCount, value);
            } else {
                return passDefaultLocalCheck(resourceWrapper, rule, acquireCount, value);
            }
        }
        //2、当规则配置的阈值类型为Threads时，获取当前资源的ParameterMetric实例，从而获取当前资源和参数取值对应的并行占用线程数。
        //如果并行占用线程数加1大于限流阈值，则决绝请求，否则放行请求
        else if (rule.getGrade() == RuleConstant.FLOW_GRADE_THREAD) {
            Set<Object> exclusionItems = rule.getParsedHotItems().keySet();
            long threadCount = getParameterMetric(resourceWrapper).getThreadCount(rule.getParamIdx(), value);
            if (exclusionItems.contains(value)) {
                int itemThreshold = rule.getParsedHotItems().get(value);
                return ++threadCount <= itemThreshold;
            }
            long threshold = (long)rule.getCount();
            return ++threadCount <= threshold;
        }

        return true;
    }

    static boolean passDefaultLocalCheck(ResourceWrapper resourceWrapper, ParamFlowRule rule, int acquireCount,
                                         Object value) {
        //1、根据资源ID获取ParameterMetric实例，从ParameterMetric实例中获取当前限流规则的令牌桶和最近一次生产令牌的时间，并将时间精确到毫秒
        ParameterMetric metric = getParameterMetric(resourceWrapper);
        //令牌桶
        CacheMap<Object, AtomicLong> tokenCounters = metric == null ? null : metric.getRuleTokenCounter(rule);
        //最近一次生产令牌的时间
        CacheMap<Object, AtomicLong> timeCounters = metric == null ? null : metric.getRuleTimeCounter(rule);

        if (tokenCounters == null || timeCounters == null) {
            return true;
        }

        // Calculate max token count (threshold)
        //2、计算限流阈值，即令牌桶能够存放的最大令牌总数（tokenCount）
        Set<Object> exclusionItems = rule.getParsedHotItems().keySet();
        long tokenCount = (long)rule.getCount();
        if (exclusionItems.contains(value)) {
            tokenCount = rule.getParsedHotItems().get(value);
        }

        if (tokenCount == 0) {
            return false;
        }
        //3、重新计算限流阈值，将当前限流阈值加上允许突增流量的数量
        long maxCount = tokenCount + rule.getBurstCount();
        if (acquireCount > maxCount) {
            return false;
        }

        while (true) {
            //4、获取当前时间，如果当前参数值首次出现“？”，则初始化生产令牌，并立即使用
            long currentTime = TimeUtil.currentTimeMillis();

            AtomicLong lastAddTokenTime = timeCounters.putIfAbsent(value, new AtomicLong(currentTime));
            if (lastAddTokenTime == null) {
                // Token never added, just replenish the tokens and consume {@code acquireCount} immediately.
                tokenCounters.putIfAbsent(value, new AtomicLong(maxCount - acquireCount));
                return true;
            }

            // Calculate the time duration since last token was added.
            //5、获取当前时间与上次生产令牌的时间间隔，如果时间间隔大于一个时间窗口，则见6，否则见7
            long passTime = currentTime - lastAddTokenTime.get();
            // A simplified token bucket algorithm that will replenish the tokens only when statistic window has passed.
            if (passTime > rule.getDurationInSec() * 1000) {
                AtomicLong oldQps = tokenCounters.putIfAbsent(value, new AtomicLong(maxCount - acquireCount));
                if (oldQps == null) {
                    // Might not be accurate here.
                    lastAddTokenTime.set(currentTime);
                    return true;
                } else {
                    //6、计算需要生产的令牌总数，并与当前令牌桶中剩余的令牌数相加得到新的令牌总数，如果新的令牌总数
                    //大于限流阈值，则使用限流阈值作为新的令牌总数，并且在令牌生产完成后立即使用，最后更新最近一次生产
                    //令牌的时间
                    long restQps = oldQps.get();
                    long toAddCount = (passTime * tokenCount) / (rule.getDurationInSec() * 1000);
                    long newQps = toAddCount + restQps > maxCount ? (maxCount - acquireCount)
                        : (restQps + toAddCount - acquireCount);

                    if (newQps < 0) {
                        return false;
                    }
                    if (oldQps.compareAndSet(restQps, newQps)) {
                        lastAddTokenTime.set(currentTime);
                        return true;
                    }
                    Thread.yield();
                }
            } else {
                //7、从令牌桶中获取令牌，如果获取成功，则放行当前请求，否则拒绝当前请求
                AtomicLong oldQps = tokenCounters.get(value);
                if (oldQps != null) {
                    long oldQpsValue = oldQps.get();
                    if (oldQpsValue - acquireCount >= 0) {
                        if (oldQps.compareAndSet(oldQpsValue, oldQpsValue - acquireCount)) {
                            return true;
                        }
                    } else {
                        return false;
                    }
                }
                Thread.yield();
            }
        }
    }

    static boolean passThrottleLocalCheck(ResourceWrapper resourceWrapper, ParamFlowRule rule, int acquireCount,
                                          Object value) {
        //1、当流量控制效果匀速排队时，ParameterMetric实例的ruleTimeCounters方法记录的是最后一个请求的期望通过时间
        ParameterMetric metric = getParameterMetric(resourceWrapper);
        CacheMap<Object, AtomicLong> timeRecorderMap = metric == null ? null : metric.getRuleTimeCounter(rule);
        if (timeRecorderMap == null) {
            return true;
        }

        // Calculate max token count (threshold)
        //2、计算限流阈值
        Set<Object> exclusionItems = rule.getParsedHotItems().keySet();
        long tokenCount = (long)rule.getCount();
        if (exclusionItems.contains(value)) {
            tokenCount = rule.getParsedHotItems().get(value);
        }

        if (tokenCount == 0) {
            return false;
        }
        //3、计算请求通过的时间间隔，例如，当acquireCount等于1、限流阈值配置为200QOS且时间窗口大小为1秒时，计算出来的costTime等于5毫秒，即
        //5毫秒只允许通过一个请求
        long costTime = Math.round(1.0 * 1000 * acquireCount * rule.getDurationInSec() / tokenCount);
        while (true) {
            //4、计算当前请求的期望通过时间，值为最近一次请求的期望通过时间与请求通过的时间间隔之和，而最近一次请求的期望通过时间就是虚拟队列
            //中队列尾部的那个请求的期望通过时间
            long currentTime = TimeUtil.currentTimeMillis();
            AtomicLong timeRecorder = timeRecorderMap.putIfAbsent(value, new AtomicLong(currentTime));
            if (timeRecorder == null) {
                return true;
            }
            //AtomicLong timeRecorder = timeRecorderMap.get(value);
            long lastPassTime = timeRecorder.get();
            long expectedTime = lastPassTime + costTime;
            //5、排队等待时间等于期望通过时间与当前时间的间隔，如果排队等待时间大于限流规则配置的最大等待时间，则拒绝当前请求，否则将当前请求
            //放入虚拟队列中等待，并计算出当前请求需要等待的时间，让当前线程休眠指定时长之后再放行该请求
            if (expectedTime <= currentTime || expectedTime - currentTime < rule.getMaxQueueingTimeMs()) {
                AtomicLong lastPastTimeRef = timeRecorderMap.get(value);
                if (lastPastTimeRef.compareAndSet(lastPassTime, currentTime)) {
                    long waitTime = expectedTime - currentTime;
                    if (waitTime > 0) {
                        lastPastTimeRef.set(expectedTime);
                        try {
                            TimeUnit.MILLISECONDS.sleep(waitTime);
                        } catch (InterruptedException e) {
                            RecordLog.warn("passThrottleLocalCheck: wait interrupted", e);
                        }
                    }
                    return true;
                } else {
                    Thread.yield();
                }
            } else {
                return false;
            }
        }
    }

    private static ParameterMetric getParameterMetric(ResourceWrapper resourceWrapper) {
        // Should not be null.
        return ParameterMetricStorage.getParamMetric(resourceWrapper);
    }

    @SuppressWarnings("unchecked")
    private static Collection<Object> toCollection(Object value) {
        if (value instanceof Collection) {
            return (Collection<Object>)value;
        } else if (value.getClass().isArray()) {
            List<Object> params = new ArrayList<Object>();
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                Object param = Array.get(value, i);
                params.add(param);
            }
            return params;
        } else {
            return Collections.singletonList(value);
        }
    }

    private static boolean passClusterCheck(ResourceWrapper resourceWrapper, ParamFlowRule rule, int count,
                                            Object value) {
        try {
            Collection<Object> params = toCollection(value);

            TokenService clusterService = pickClusterService();
            if (clusterService == null) {
                // No available cluster client or server, fallback to local or
                // pass in need.
                return fallbackToLocalOrPass(resourceWrapper, rule, count, params);
            }

            TokenResult result = clusterService.requestParamToken(rule.getClusterConfig().getFlowId(), count, params);
            switch (result.getStatus()) {
                case TokenResultStatus.OK:
                    return true;
                case TokenResultStatus.BLOCKED:
                    return false;
                default:
                    return fallbackToLocalOrPass(resourceWrapper, rule, count, params);
            }
        } catch (Throwable ex) {
            RecordLog.warn("[ParamFlowChecker] Request cluster token for parameter unexpected failed", ex);
            return fallbackToLocalOrPass(resourceWrapper, rule, count, value);
        }
    }

    private static boolean fallbackToLocalOrPass(ResourceWrapper resourceWrapper, ParamFlowRule rule, int count,
                                                 Object value) {
        if (rule.getClusterConfig().isFallbackToLocalWhenFail()) {
            return passLocalCheck(resourceWrapper, rule, count, value);
        } else {
            // The rule won't be activated, just pass.
            return true;
        }
    }

    private static TokenService pickClusterService() {
        if (ClusterStateManager.isClient()) {
            return TokenClientProvider.getClient();
        }
        if (ClusterStateManager.isServer()) {
            return EmbeddedClusterTokenServerProvider.getServer();
        }
        return null;
    }

    private ParamFlowChecker() {
    }
}
