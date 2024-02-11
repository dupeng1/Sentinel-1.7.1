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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.node.DefaultNode;
import com.alibaba.csp.sentinel.slots.block.AbstractRule;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;

/**
 * Rules for "hot-spot" frequent parameter flow control.
 *
 * @author jialiang.linjl
 * @author Eric Zhao
 * @since 0.2.0
 */

/**
 * 参数限流规则
 */
public class ParamFlowRule extends AbstractRule {

    public ParamFlowRule() {}

    public ParamFlowRule(String resourceName) {
        setResource(resourceName);
    }

    /**
     * The threshold type of flow control (0: thread count, 1: QPS).
     */
    //限流规则的阈值类型，支持的类型与FlowRule相同
    private int grade = RuleConstant.FLOW_GRADE_QPS;

    /**
     * Parameter index.
     */
    //参数索引，ParamFlowChecker根据限流规则的参数索引获取参数的只，下标从0开始。例如apiHello(String name)方法只有一个参数，索引为0对应name参数
    private Integer paramIdx;

    /**
     * The threshold count.
     */
    //阈值，支持的类型与FlowRule相同
    private double count;

    /**
     * Traffic shaping behavior (since 1.6.0).
     */
    //流量控制效果，支持的类型与FlowRule相同，但只支持快速失败和匀速排队
    private int controlBehavior = RuleConstant.CONTROL_BEHAVIOR_DEFAULT;
    //实现匀速排队流量控制效果的虚拟队列最大等待时间，超过该值的请求被抛弃，支持的类型与FlowRule相同
    private int maxQueueingTimeMs = 0;
    //支持的突发流量总数
    private int burstCount = 0;
    //统计指标数据的时间窗口大小，单位为秒
    private long durationInSec = 1;

    /**
     * Original exclusion items of parameters.
     */
    private List<ParamFlowItem> paramFlowItemList = new ArrayList<ParamFlowItem>();

    /**
     * Parsed exclusion items of parameters. Only for internal use.
     */
    private Map<Object, Integer> hotItems = new HashMap<Object, Integer>();

    /**
     * Indicating whether the rule is for cluster mode.
     */
    private boolean clusterMode = false;
    /**
     * Cluster mode specific config for parameter flow rule.
     */
    private ParamFlowClusterConfig clusterConfig;

    public int getControlBehavior() {
        return controlBehavior;
    }

    public ParamFlowRule setControlBehavior(int controlBehavior) {
        this.controlBehavior = controlBehavior;
        return this;
    }

    public int getMaxQueueingTimeMs() {
        return maxQueueingTimeMs;
    }

    public ParamFlowRule setMaxQueueingTimeMs(int maxQueueingTimeMs) {
        this.maxQueueingTimeMs = maxQueueingTimeMs;
        return this;
    }

    public int getBurstCount() {
        return burstCount;
    }

    public ParamFlowRule setBurstCount(int burstCount) {
        this.burstCount = burstCount;
        return this;
    }

    public long getDurationInSec() {
        return durationInSec;
    }

    public ParamFlowRule setDurationInSec(long durationInSec) {
        this.durationInSec = durationInSec;
        return this;
    }

    public int getGrade() {
        return grade;
    }

    public ParamFlowRule setGrade(int grade) {
        this.grade = grade;
        return this;
    }

    public Integer getParamIdx() {
        return paramIdx;
    }

    public ParamFlowRule setParamIdx(Integer paramIdx) {
        this.paramIdx = paramIdx;
        return this;
    }

    public double getCount() {
        return count;
    }

    public ParamFlowRule setCount(double count) {
        this.count = count;
        return this;
    }

    public List<ParamFlowItem> getParamFlowItemList() {
        return paramFlowItemList;
    }

    public ParamFlowRule setParamFlowItemList(List<ParamFlowItem> paramFlowItemList) {
        this.paramFlowItemList = paramFlowItemList;
        return this;
    }

    public Integer retrieveExclusiveItemCount(Object value) {
        if (value == null || hotItems == null) {
            return null;
        }
        return hotItems.get(value);
    }

    Map<Object, Integer> getParsedHotItems() {
        return hotItems;
    }

    ParamFlowRule setParsedHotItems(Map<Object, Integer> hotItems) {
        this.hotItems = hotItems;
        return this;
    }

    public boolean isClusterMode() {
        return clusterMode;
    }

    public ParamFlowRule setClusterMode(boolean clusterMode) {
        this.clusterMode = clusterMode;
        return this;
    }

    public ParamFlowClusterConfig getClusterConfig() {
        return clusterConfig;
    }

    public ParamFlowRule setClusterConfig(ParamFlowClusterConfig clusterConfig) {
        this.clusterConfig = clusterConfig;
        return this;
    }

    @Override
    @Deprecated
    public boolean passCheck(Context context, DefaultNode node, int count, Object... args) {
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        if (!super.equals(o)) { return false; }

        ParamFlowRule that = (ParamFlowRule)o;

        if (grade != that.grade) { return false; }
        if (Double.compare(that.count, count) != 0) { return false; }
        if (controlBehavior != that.controlBehavior) { return false; }
        if (maxQueueingTimeMs != that.maxQueueingTimeMs) { return false; }
        if (burstCount != that.burstCount) { return false; }
        if (durationInSec != that.durationInSec) { return false; }
        if (clusterMode != that.clusterMode) { return false; }
        if (!Objects.equals(paramIdx, that.paramIdx)) { return false; }
        if (!Objects.equals(paramFlowItemList, that.paramFlowItemList)) { return false; }
        return Objects.equals(clusterConfig, that.clusterConfig);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        long temp;
        result = 31 * result + grade;
        result = 31 * result + (paramIdx != null ? paramIdx.hashCode() : 0);
        temp = Double.doubleToLongBits(count);
        result = 31 * result + (int)(temp ^ (temp >>> 32));
        result = 31 * result + controlBehavior;
        result = 31 * result + maxQueueingTimeMs;
        result = 31 * result + burstCount;
        result = 31 * result + (int)(durationInSec ^ (durationInSec >>> 32));
        result = 31 * result + (paramFlowItemList != null ? paramFlowItemList.hashCode() : 0);
        result = 31 * result + (clusterMode ? 1 : 0);
        result = 31 * result + (clusterConfig != null ? clusterConfig.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ParamFlowRule{" +
            "grade=" + grade +
            ", paramIdx=" + paramIdx +
            ", count=" + count +
            ", controlBehavior=" + controlBehavior +
            ", maxQueueingTimeMs=" + maxQueueingTimeMs +
            ", burstCount=" + burstCount +
            ", durationInSec=" + durationInSec +
            ", paramFlowItemList=" + paramFlowItemList +
            ", clusterMode=" + clusterMode +
            ", clusterConfig=" + clusterConfig +
            '}';
    }
}
