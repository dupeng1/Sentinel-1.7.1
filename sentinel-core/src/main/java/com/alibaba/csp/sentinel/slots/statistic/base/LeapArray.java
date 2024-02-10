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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.ReentrantLock;

import com.alibaba.csp.sentinel.util.AssertUtil;
import com.alibaba.csp.sentinel.util.TimeUtil;

/**
 * <p>
 * Basic data structure for statistic metrics in Sentinel.
 * </p>
 * <p>
 * Leap array use sliding window algorithm to count data. Each bucket cover {@code windowLengthInMs} time span,
 * and the total time span is {@link #intervalInMs}, so the total bucket amount is:
 * {@code sampleCount = intervalInMs / windowLengthInMs}.
 * </p>
 *
 * @param <T> type of statistic data
 * @author jialiang.linjl
 * @author Eric Zhao
 * @author Carpenter Lee
 */

/**
 * 1、当前时间戳是1577017699235，Bucket时间窗口大小是1秒，将时间戳的毫秒部分全部替换为0，就能得到Bucket的时间窗口的开始时间戳为1577017699000
 * @param <T>
 */
public abstract class LeapArray<T> {

    //Bucket【时间窗口】大小，例如1000代表1s
    protected int windowLengthInMs;
    protected int sampleCount;
    protected int intervalInMs;
    //当前【滑动窗口】Bucket数组，例如滑动窗口是1分钟，则Bucket数组存储8点29分开始每一秒【时间窗口】
    protected final AtomicReferenceArray<WindowWrap<T>> array;

    /**
     * The conditional (predicate) update lock is used only when current bucket is deprecated.
     */
    private final ReentrantLock updateLock = new ReentrantLock();

    /**
     * The total bucket count is: {@code sampleCount = intervalInMs / windowLengthInMs}.
     *
     * @param sampleCount  bucket count of the sliding window
     * @param intervalInMs the total time interval of this {@link LeapArray} in milliseconds
     */
    public LeapArray(int sampleCount, int intervalInMs) {
        AssertUtil.isTrue(sampleCount > 0, "bucket count is invalid: " + sampleCount);
        AssertUtil.isTrue(intervalInMs > 0, "total time interval of the sliding window should be positive");
        AssertUtil.isTrue(intervalInMs % sampleCount == 0, "time span needs to be evenly divided");

        this.windowLengthInMs = intervalInMs / sampleCount;
        this.intervalInMs = intervalInMs;
        this.sampleCount = sampleCount;

        this.array = new AtomicReferenceArray<>(sampleCount);
    }

    /**
     * Get the bucket at current timestamp.
     *
     * @return the bucket at current timestamp
     */
    public WindowWrap<T> currentWindow() {
        return currentWindow(TimeUtil.currentTimeMillis());
    }

    /**
     * Create a new statistic value for bucket.
     *
     * @param timeMillis current time in milliseconds
     * @return the new empty bucket
     */
    public abstract T newEmptyBucket(long timeMillis);

    /**
     * Reset given bucket to provided start time and reset the value.
     *
     * @param startTime  the start time of the bucket in milliseconds
     * @param windowWrap current bucket
     * @return new clean bucket at given start time
     */
    protected abstract WindowWrap<T> resetWindowTo(WindowWrap<T> windowWrap, long startTime);


    //定位当前时间戳对应的Bucket在滑动窗口的Bucket数组中的位置
    private int calculateTimeIdx(/*@Valid*/ long timeMillis) {
        long timeId = timeMillis / windowLengthInMs;
        // Calculate current index so we can map the timestamp to the leap array.
        return (int)(timeId % array.length());
    }

    //计算Bucket的时间窗口的开始时间戳
    protected long calculateWindowStart(/*@Valid*/ long timeMillis) {
        return timeMillis - timeMillis % windowLengthInMs;
    }

    /**
     * Get bucket item at provided timestamp.
     *
     * @param timeMillis a valid timestamp in milliseconds
     * @return current bucket item at provided timestamp if the time is valid; null if time is invalid
     */
    //根据当前时间戳定位Bucket的算法
    public WindowWrap<T> currentWindow(long timeMillis) {
        if (timeMillis < 0) {
            return null;
        }
        //通过当前时间戳计算出当前【时间窗口】的Bucket在数据中的索引
        int idx = calculateTimeIdx(timeMillis);
        // Calculate current bucket start time.
        //计算Bucket的时间窗口的开始时间戳
        long windowStart = calculateWindowStart(timeMillis);

        /*
         * Get bucket item at given time from the array.
         *
         * (1) Bucket is absent, then just create a new bucket and CAS update to circular array.
         * (2) Bucket is up-to-date, then just return the bucket.
         * (3) Bucket is deprecated, then reset current bucket and clean all deprecated buckets.
         */
        //从数组中获取Bucket
        while (true) {
            //从数组中获取Bucket（Old Bucket）
            WindowWrap<T> old = array.get(idx);
            //一般时项目启动时或久未有请求时，或者数组还没有存储满，没有到复用阶段时
            if (old == null) {
                /*
                 *     B0       B1      B2    NULL      B4
                 * ||_______|_______|_______|_______|_______||___
                 * 200     400     600     800     1000    1200  timestamp
                 *                             ^
                 *                          time=888
                 *            bucket is empty, so create new and update
                 *
                 * If the old bucket is absent, then we create a new bucket at {@code windowStart},
                 * then try to update circular array via a CAS operation. Only one thread can
                 * succeed to update, while other threads yield its time slice.
                 */
                //创建新的Bucket，并创建一个Bucket包装器
                WindowWrap<T> window = new WindowWrap<T>(windowLengthInMs, windowStart, newEmptyBucket(timeMillis));
                //CAS写入，确保线程安全
                if (array.compareAndSet(idx, null, window)) {
                    // Successfully updated, return the created bucket.
                    return window;
                } else {
                    // Contention failed, the thread will yield its time slice to wait for bucket available.
                    Thread.yield();
                }
            } else if (windowStart == old.windowStart()) {
                //当Old Bucket不为空，且Old Bucket的【时间窗口】的开始时间戳与当前计算得到的New Bucket的【时间窗口】的开始时间戳相等时，该
                //Bucket就是当前要找的Bucket，将此Bucket直接返回
                /*
                 *     B0       B1      B2     B3      B4
                 * ||_______|_______|_______|_______|_______||___
                 * 200     400     600     800     1000    1200  timestamp
                 *                             ^
                 *                          time=888
                 *            startTime of Bucket 3: 800, so it's up-to-date
                 *
                 * If current {@code windowStart} is equal to the start timestamp of old bucket,
                 * that means the time is within the bucket, so directly return the bucket.
                 */
                return old;
            } else if (windowStart > old.windowStart()) {
                //当计算出New Bucket的时间窗口的开始时间戳大于当前数组中的idx处存储的Old Bucket的时间窗口的开始时间戳时，可以复用
                //这个Old Bucket，确保线程能够安全地重置Bucket，并返回Bucket
                /*
                 *   (old)
                 *             B0       B1      B2    NULL      B4
                 * |_______||_______|_______|_______|_______|_______||___
                 * ...    1200     1400    1600    1800    2000    2200  timestamp
                 *                              ^
                 *                           time=1676
                 *          startTime of Bucket 2: 400, deprecated, should be reset
                 *
                 * If the start timestamp of old bucket is behind provided time, that means
                 * the bucket is deprecated. We have to reset the bucket to current {@code windowStart}.
                 * Note that the reset and clean-up operations are hard to be atomic,
                 * so we need a update lock to guarantee the correctness of bucket update.
                 *
                 * The update lock is conditional (tiny scope) and will take effect only when
                 * bucket is deprecated, so in most cases it won't lead to performance loss.
                 */
                if (updateLock.tryLock()) {
                    try {
                        // Successfully get the update lock, now we reset the bucket.
                        return resetWindowTo(old, windowStart);
                    } finally {
                        updateLock.unlock();
                    }
                } else {
                    // Contention failed, the thread will yield its time slice to wait for bucket available.
                    Thread.yield();
                }
            } else if (windowStart < old.windowStart()) {
                //当计算出New Bucket的时间窗口的开始时间戳小于当前数组中的idx处存储的Old Bucket的时间窗口的开始时间戳时，
                //直接返回一个空的Bucket，因为时间不会倒退
                // Should not go through here, as the provided time is already behind.
                return new WindowWrap<T>(windowLengthInMs, windowStart, newEmptyBucket(timeMillis));
            }
        }
    }

    /**
     * Get the previous bucket item before provided timestamp.
     *
     * @param timeMillis a valid timestamp in milliseconds
     * @return the previous bucket item before provided timestamp
     */
    /**
     * 获取当前时间戳的前一个Bucket
     * 1、根据当前时间戳计算出当前Bucket的【时间窗口】的开始时间戳
     * 2、使用当前Bucket的【时间窗口】的开始时间戳减去Bucket的时间窗口大小就能定位出前一个Bucket
     * @param timeMillis
     * @return
     */
    public WindowWrap<T> getPreviousWindow(long timeMillis) {
        if (timeMillis < 0) {
            return null;
        }
        int idx = calculateTimeIdx(timeMillis - windowLengthInMs);
        timeMillis = timeMillis - windowLengthInMs;
        WindowWrap<T> wrap = array.get(idx);

        if (wrap == null || isWindowDeprecated(wrap)) {
            return null;
        }

        if (wrap.windowStart() + windowLengthInMs < (timeMillis)) {
            return null;
        }

        return wrap;
    }

    /**
     * Get the previous bucket item for current timestamp.
     *
     * @return the previous bucket item for current timestamp
     */
    public WindowWrap<T> getPreviousWindow() {
        return getPreviousWindow(TimeUtil.currentTimeMillis());
    }

    /**
     * Get statistic value from bucket for provided timestamp.
     *
     * @param timeMillis a valid timestamp in milliseconds
     * @return the statistic value if bucket for provided timestamp is up-to-date; otherwise null
     */
    public T getWindowValue(long timeMillis) {
        if (timeMillis < 0) {
            return null;
        }
        int idx = calculateTimeIdx(timeMillis);

        WindowWrap<T> bucket = array.get(idx);

        if (bucket == null || !bucket.isTimeInWindow(timeMillis)) {
            return null;
        }

        return bucket.value();
    }

    /**
     * Check if a bucket is deprecated, which means that the bucket
     * has been behind for at least an entire window time span.
     *
     * @param windowWrap a non-null bucket
     * @return true if the bucket is deprecated; otherwise false
     */
    public boolean isWindowDeprecated(/*@NonNull*/ WindowWrap<T> windowWrap) {
        return isWindowDeprecated(TimeUtil.currentTimeMillis(), windowWrap);
    }

    public boolean isWindowDeprecated(long time, WindowWrap<T> windowWrap) {
        return time - windowWrap.windowStart() > intervalInMs;
    }

    /**
     * Get valid bucket list for entire sliding window.
     * The list will only contain "valid" buckets.
     *
     * @return valid bucket list for entire sliding window.
     */
    public List<WindowWrap<T>> list() {
        return list(TimeUtil.currentTimeMillis());
    }

    public List<WindowWrap<T>> list(long validTime) {
        int size = array.length();
        List<WindowWrap<T>> result = new ArrayList<WindowWrap<T>>(size);

        for (int i = 0; i < size; i++) {
            WindowWrap<T> windowWrap = array.get(i);
            if (windowWrap == null || isWindowDeprecated(validTime, windowWrap)) {
                continue;
            }
            result.add(windowWrap);
        }

        return result;
    }

    /**
     * Get all buckets for entire sliding window including deprecated buckets.
     *
     * @return all buckets for entire sliding window
     */
    public List<WindowWrap<T>> listAll() {
        int size = array.length();
        List<WindowWrap<T>> result = new ArrayList<WindowWrap<T>>(size);

        for (int i = 0; i < size; i++) {
            WindowWrap<T> windowWrap = array.get(i);
            if (windowWrap == null) {
                continue;
            }
            result.add(windowWrap);
        }

        return result;
    }

    /**
     * Get aggregated value list for entire sliding window.
     * The list will only contain value from "valid" buckets.
     *
     * @return aggregated value list for entire sliding window
     */
    public List<T> values() {
        return values(TimeUtil.currentTimeMillis());
    }

    public List<T> values(long timeMillis) {
        if (timeMillis < 0) {
            return new ArrayList<T>();
        }
        int size = array.length();
        List<T> result = new ArrayList<T>(size);

        for (int i = 0; i < size; i++) {
            WindowWrap<T> windowWrap = array.get(i);
            if (windowWrap == null || isWindowDeprecated(timeMillis, windowWrap)) {
                continue;
            }
            result.add(windowWrap.value());
        }
        return result;
    }

    /**
     * Get the valid "head" bucket of the sliding window for provided timestamp.
     * Package-private for test.
     *
     * @param timeMillis a valid timestamp in milliseconds
     * @return the "head" bucket if it exists and is valid; otherwise null
     */
    WindowWrap<T> getValidHead(long timeMillis) {
        // Calculate index for expected head time.
        int idx = calculateTimeIdx(timeMillis + windowLengthInMs);

        WindowWrap<T> wrap = array.get(idx);
        if (wrap == null || isWindowDeprecated(wrap)) {
            return null;
        }

        return wrap;
    }

    /**
     * Get the valid "head" bucket of the sliding window at current timestamp.
     *
     * @return the "head" bucket if it exists and is valid; otherwise null
     */
    public WindowWrap<T> getValidHead() {
        return getValidHead(TimeUtil.currentTimeMillis());
    }

    /**
     * Get sample count (total amount of buckets).
     *
     * @return sample count
     */
    public int getSampleCount() {
        return sampleCount;
    }

    /**
     * Get total interval length of the sliding window in milliseconds.
     *
     * @return interval in second
     */
    public int getIntervalInMs() {
        return intervalInMs;
    }

    /**
     * Get total interval length of the sliding window.
     *
     * @return interval in second
     */
    public double getIntervalInSecond() {
        return intervalInMs / 1000.0;
    }

    public void debug(long time) {
        StringBuilder sb = new StringBuilder();
        List<WindowWrap<T>> lists = list(time);
        sb.append("Thread_").append(Thread.currentThread().getId()).append("_");
        for (WindowWrap<T> window : lists) {
            sb.append(window.windowStart()).append(":").append(window.value().toString());
        }
        System.out.println(sb.toString());
    }

    public long currentWaiting() {
        // TODO: default method. Should remove this later.
        return 0;
    }

    public void addWaiting(long time, int acquireCount) {
        // Do nothing by default.
        throw new UnsupportedOperationException();
    }
}
