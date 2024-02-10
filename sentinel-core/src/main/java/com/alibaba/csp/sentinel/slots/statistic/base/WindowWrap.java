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
package com.alibaba.csp.sentinel.slots.statistic.base;

/**
 * Wrapper entity class for a period of time window.
 *
 * @param <T> data type
 * @author jialiang.linjl
 * @author Eric Zhao
 */

/**
 * 记录Bucket的时间窗口信息
 * 1、假设Bucket的时间窗口大小为1秒，那么Bucket统计的就是1秒内的请求成功总数、请求异常总数、总耗时等指标数据
 * 2、如果时间窗口为[1577017699000, 1577017699999]，那么1577017699000就是该Bucket的时间窗口的开始时间戳，1000毫秒就是该Bucket的时间窗口大小
 * 3、当收到一个请求时，可以根据收到请求时的时间戳和【滑动窗口】大小计算出一个索引值，从滑动窗口（WindowWrap数组）中获取一个WindowWrap类，
 * 从而获取WindowWrap包装的Bucket，并调用Bucket实例的add方法统计指标
 * @param <T>
 */
public class WindowWrap<T> {

    /**
     * Time length of a single window bucket in milliseconds.
     */
    //Bucket的【时间窗口】大小
    private final long windowLengthInMs;

    /**
     * Start timestamp of the window in milliseconds.
     */
    //【时间窗口】的开始时间戳
    private long windowStart;

    /**
     * Statistic data.
     */
    //被包装的Bucket
    private T value;

    /**
     * @param windowLengthInMs a single window bucket's time length in milliseconds.
     * @param windowStart      the start timestamp of the window
     * @param value            statistic data
     */
    public WindowWrap(long windowLengthInMs, long windowStart, T value) {
        this.windowLengthInMs = windowLengthInMs;
        this.windowStart = windowStart;
        this.value = value;
    }

    public long windowLength() {
        return windowLengthInMs;
    }

    public long windowStart() {
        return windowStart;
    }

    public T value() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }

    /**
     * Reset start timestamp of current bucket to provided time.
     *
     * @param startTime valid start timestamp
     * @return bucket after reset
     */
    public WindowWrap<T> resetTo(long startTime) {
        this.windowStart = startTime;
        return this;
    }

    /**
     * Check whether given timestamp is in current bucket.
     *
     * @param timeMillis valid timestamp in ms
     * @return true if the given time is in current bucket, otherwise false
     * @since 1.5.0
     */
    public boolean isTimeInWindow(long timeMillis) {
        return windowStart <= timeMillis && timeMillis < windowStart + windowLengthInMs;
    }

    @Override
    public String toString() {
        return "WindowWrap{" +
            "windowLengthInMs=" + windowLengthInMs +
            ", windowStart=" + windowStart +
            ", value=" + value +
            '}';
    }
}
