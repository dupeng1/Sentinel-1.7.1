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

import com.alibaba.csp.sentinel.util.TimeUtil;
import com.alibaba.csp.sentinel.node.Node;
import com.alibaba.csp.sentinel.slots.block.flow.TrafficShapingController;

/**
 * <p>
 * The principle idea comes from Guava. However, the calculation of Guava is
 * rate-based, which means that we need to translate rate to QPS.
 * </p>
 *
 * <p>
 * Requests arriving at the pulse may drag down long idle systems even though it
 * has a much larger handling capability in stable period. It usually happens in
 * scenarios that require extra time for initialization, e.g. DB establishes a connection,
 * connects to a remote service, and so on. That’s why we need “warm up”.
 * </p>
 *
 * <p>
 * Sentinel's "warm-up" implementation is based on the Guava's algorithm.
 * However, Guava’s implementation focuses on adjusting the request interval,
 * which is similar to leaky bucket. Sentinel pays more attention to
 * controlling the count of incoming requests per second without calculating its interval,
 * which resembles token bucket algorithm.
 * </p>
 *
 * <p>
 * The remaining tokens in the bucket is used to measure the system utility.
 * Suppose a system can handle b requests per second. Every second b tokens will
 * be added into the bucket until the bucket is full. And when system processes
 * a request, it takes a token from the bucket. The more tokens left in the
 * bucket, the lower the utilization of the system; when the token in the token
 * bucket is above a certain threshold, we call it in a "saturation" state.
 * </p>
 *
 * <p>
 * Base on Guava’s theory, there is a linear equation we can write this in the
 * form y = m * x + b where y (a.k.a y(x)), or qps(q)), is our expected QPS
 * given a saturated period (e.g. 3 minutes in), m is the rate of change from
 * our cold (minimum) rate to our stable (maximum) rate, x (or q) is the
 * occupied token.
 * </p>
 *
 * @author jialiang.linjl
 */

/**
 * 1、在应用升级重启时，应用自身需要一个预热过程，因为只有预热之后才能到达稳定的性能状态，在接口预热阶段可以完成一些单例对象的创建、线程池的创建，
 * 完成各种连接池的初始化并执行首次需要加锁执行的代码块
 * 2、冷启动并非只在应用重启时需要，例如：在一段时间内没有访问的情况下，连接池中存在大量过期连接需要待下次使用才移除并创建新的连接、一些热点数据
 * 缓存过期需要重新查找数据库并写入缓存
 * 3、支持冷启动周期，即冷启动的时长，默认为10秒。控制流量在冷启动周期内平缓地增长到限流阈值。例如某个接口限流为200QPS，预热时间为10秒，那么在这10
 * 秒内，相当于每秒的限流阈值分别为5QPS、15QPS、35QPS、70QPS、90QPS、115QPS、145QPS、170QPS、190QPS、200QPS
 * 4、   stableInterval：稳定产生令牌的时间间隔
 *      coldInterval：冷启动产生令牌的最大时间间隔，等于稳定产生令牌的时间间隔乘以冷启动系数
 *      thresholdPermits：令牌桶中剩余令牌数的阈值，介于以正常速率生产令牌还是以冷启动速率生产令牌的阈值，是判断是否需要进入冷启动阶段的依据
 *      maxPermits：允许令牌桶中存放的最大令牌数
 *      slope：直线的斜率
 *      warmupPeriod：预热时长，即冷启动周期
 *
 *
 */
public class WarmUpController implements TrafficShapingController {
    //限流阈值的 QPS
    protected double count;
    //冷启动系数，默认为 3
    private int coldFactor;
    //在稳定的令牌生产速率下，令牌桶中存储的令牌数
    protected int warningToken = 0;
    //令牌桶的最大容量
    private int maxToken;
    //斜率，每秒放行请求数的增长速率
    protected double slope;
    //令牌桶当前存储的令牌数
    protected AtomicLong storedTokens = new AtomicLong(0);
    //上一次生产令牌的时间
    protected AtomicLong lastFilledTime = new AtomicLong(0);

    public WarmUpController(double count, int warmUpPeriodInSec, int coldFactor) {
        construct(count, warmUpPeriodInSec, coldFactor);
    }

    public WarmUpController(double count, int warmUpPeriodInSec) {
        construct(count, warmUpPeriodInSec, 3);
    }

    private void construct(double count, int warmUpPeriodInSec, int coldFactor) {

        if (coldFactor <= 1) {
            throw new IllegalArgumentException("Cold factor should be larger than 1");
        }

        this.count = count;

        this.coldFactor = coldFactor;

        // thresholdPermits = 0.5 * warmupPeriod / stableInterval.
        // warningToken = 100;
        warningToken = (int)(warmUpPeriodInSec * count) / (coldFactor - 1);
        // / maxPermits = thresholdPermits + 2 * warmupPeriod /
        // (stableInterval + coldInterval)
        // maxToken = 200
        maxToken = warningToken + (int)(2 * warmUpPeriodInSec * count / (1.0 + coldFactor));

        // slope
        // slope = (coldIntervalMicros - stableIntervalMicros) / (maxPermits
        // - thresholdPermits);
        slope = (coldFactor - 1.0) / count / (maxToken - warningToken);

    }

    @Override
    public boolean canPass(Node node, int acquireCount) {
        return canPass(node, acquireCount, false);
    }

    @Override
    public boolean canPass(Node node, int acquireCount, boolean prioritized) {
        //当前时间窗口通过的QPS
        long passQps = (long) node.passQps();
        //前一个时间窗口通过的QPS
        long previousQps = (long) node.previousPassQps();
        //resync
        syncToken(previousQps);

        // 开始计算它的斜率
        // 如果进入了警戒线，开始调整他的qps
        long restToken = storedTokens.get();
        //若令牌桶中存放的令牌数超过警戒线，则进入冷启动节点，调整QPS
        if (restToken >= warningToken) {
            //超过warningToken的当前令牌数
            long aboveToken = restToken - warningToken;
            // 1.0/count计算出来的值是正常情况下每隔多少毫秒生产一个令牌
            double warningQps = Math.nextUp(1.0 / (aboveToken * slope + 1.0 / count));
            //小于warningQps才放行
            if (passQps + acquireCount <= warningQps) {
                return true;
            }
        } else {
            //未超过警戒线的情况下按正常限流，若放行当前请求之后会导致通过的QPS超过阈值，则拦截当前请求，否则放行
            if (passQps + acquireCount <= count) {
                return true;
            }
        }

        return false;
    }

    /**
     * Sentinel并不是每通过一个请求就从令牌桶中移除一个令牌，而是每秒更新令牌数时再扣除上一秒消耗的令牌数，
     * 上一秒消耗的令牌数就等于上一秒通过的请求数
     * @param passQps 上一秒通过的QPS总数
     */
    protected void syncToken(long passQps) {
        long currentTime = TimeUtil.currentTimeMillis();
        //去掉毫秒，取秒
        currentTime = currentTime - currentTime % 1000;
        long oldLastFillTime = lastFilledTime.get();
        //控制每秒只更新一次
        if (currentTime <= oldLastFillTime) {
            return;
        }

        long oldValue = storedTokens.get();
        //计算新的令牌桶中存储的令牌数
        long newValue = coolDownTokens(currentTime, passQps);

        if (storedTokens.compareAndSet(oldValue, newValue)) {
            //storedTokens扣减上个时间窗口的QPS
            long currentValue = storedTokens.addAndGet(0 - passQps);
            if (currentValue < 0) {
                storedTokens.set(0L);
            }
            lastFilledTime.set(currentTime);
        }

    }

    /**
     *
     * @param currentTime   当前时间戳，单位为毫秒，但后秒3位全为0
     * @param passQps   上一秒通过的QPS
     * @return
     */
    private long coolDownTokens(long currentTime, long passQps) {
        long oldValue = storedTokens.get();
        long newValue = oldValue;

        // 添加令牌的判断前提条件:
        // 当令牌的消耗程度远远低于警戒线的时候
        //当令牌桶中剩余的令牌数小于当前秒能生产的令牌数时，currentTime - lastFilledTime.get()为当前时间与上一次生产令牌时间的时间间隔
        if (oldValue < warningToken) {
            newValue = (long)(oldValue + (currentTime - lastFilledTime.get()) * count / 1000);
        }
        //当令牌桶中剩余的令牌数大于当前每秒能生产的令牌数时
        else if (oldValue > warningToken) {
            if (passQps < (int)count / coldFactor) {
                newValue = (long)(oldValue + (currentTime - lastFilledTime.get()) * count / 1000);
            }
        }
        return Math.min(newValue, maxToken);
    }

}
