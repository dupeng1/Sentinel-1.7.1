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

import com.alibaba.csp.sentinel.slots.block.ClusterRuleConstant;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;

/**
 * Flow rule config in cluster mode.
 *
 * @author Eric Zhao
 * @since 1.4.0
 */

/**
 * 集群限流规则
 */
public class ClusterFlowConfig {

    /**
     * Global unique ID.
     */
    //集群限流规则的全局唯一ID
    private Long flowId;

    /**
     * Threshold type (average by local value or global value).
     */
    //集群限流阈值类型，支持单机均摊和集群总阈值两种集群限流阈值类型
            //单机均摊：将当前连接到集群限流服务端的集群限流客户端节点数乘以规则配置的count的结果作为集群的QPS限流阈值
            //集群总阈值：将规则配置的count作为集群的QPS限流阈值
    private int thresholdType = ClusterRuleConstant.FLOW_THRESHOLD_AVG_LOCAL;
    //失败时是否回退为本地限流模式
    private boolean fallbackToLocalWhenFail = true;

    /**
     * 0: normal.
     */
    private int strategy = ClusterRuleConstant.FLOW_CLUSTER_STRATEGY_NORMAL;
    //滑动窗口构造方法的参数之一，指定WindowWrap的数组大小
    private int sampleCount = ClusterRuleConstant.DEFAULT_CLUSTER_SAMPLE_COUNT;
    /**
     * The time interval length of the statistic sliding window (in milliseconds)
     */
    // 滑动窗口构造方法的参数之一，指定滑动窗口的周期
    private int windowIntervalMs = RuleConstant.DEFAULT_WINDOW_INTERVAL_MS;

    public Long getFlowId() {
        return flowId;
    }

    public ClusterFlowConfig setFlowId(Long flowId) {
        this.flowId = flowId;
        return this;
    }

    public int getThresholdType() {
        return thresholdType;
    }

    public ClusterFlowConfig setThresholdType(int thresholdType) {
        this.thresholdType = thresholdType;
        return this;
    }

    public int getStrategy() {
        return strategy;
    }

    public ClusterFlowConfig setStrategy(int strategy) {
        this.strategy = strategy;
        return this;
    }

    public boolean isFallbackToLocalWhenFail() {
        return fallbackToLocalWhenFail;
    }

    public ClusterFlowConfig setFallbackToLocalWhenFail(boolean fallbackToLocalWhenFail) {
        this.fallbackToLocalWhenFail = fallbackToLocalWhenFail;
        return this;
    }

    public int getSampleCount() {
        return sampleCount;
    }

    public ClusterFlowConfig setSampleCount(int sampleCount) {
        this.sampleCount = sampleCount;
        return this;
    }

    public int getWindowIntervalMs() {
        return windowIntervalMs;
    }

    public ClusterFlowConfig setWindowIntervalMs(int windowIntervalMs) {
        this.windowIntervalMs = windowIntervalMs;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }

        ClusterFlowConfig that = (ClusterFlowConfig)o;

        if (thresholdType != that.thresholdType) { return false; }
        if (fallbackToLocalWhenFail != that.fallbackToLocalWhenFail) { return false; }
        if (strategy != that.strategy) { return false; }
        if (sampleCount != that.sampleCount) { return false; }
        if (windowIntervalMs != that.windowIntervalMs) { return false; }
        return flowId != null ? flowId.equals(that.flowId) : that.flowId == null;
    }

    @Override
    public int hashCode() {
        int result = flowId != null ? flowId.hashCode() : 0;
        result = 31 * result + thresholdType;
        result = 31 * result + (fallbackToLocalWhenFail ? 1 : 0);
        result = 31 * result + strategy;
        result = 31 * result + sampleCount;
        result = 31 * result + windowIntervalMs;
        return result;
    }

    @Override
    public String toString() {
        return "ClusterFlowConfig{" +
            "flowId=" + flowId +
            ", thresholdType=" + thresholdType +
            ", fallbackToLocalWhenFail=" + fallbackToLocalWhenFail +
            ", strategy=" + strategy +
            ", sampleCount=" + sampleCount +
            ", windowIntervalMs=" + windowIntervalMs +
            '}';
    }
}
