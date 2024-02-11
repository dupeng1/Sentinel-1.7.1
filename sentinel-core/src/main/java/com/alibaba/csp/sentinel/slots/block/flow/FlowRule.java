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

import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.node.DefaultNode;
import com.alibaba.csp.sentinel.slots.block.AbstractRule;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;

/**
 * <p>
 * Each flow rule is mainly composed of three factors: <strong>grade</strong>,
 * <strong>strategy</strong> and <strong>controlBehavior</strong>:
 * </p>
 * <ul>
 *     <li>The {@link #grade} represents the threshold type of flow control (by QPS or thread count).</li>
 *     <li>The {@link #strategy} represents the strategy based on invocation relation.</li>
 *     <li>The {@link #controlBehavior} represents the QPS shaping behavior (actions on incoming request when QPS
 *     exceeds the threshold).</li>
 * </ul>
 *
 * @author jialiang.linjl
 * @author Eric Zhao
 */

/**
 * 限流规则配置类
 */
public class FlowRule extends AbstractRule {

    public FlowRule() {
        super();
        setLimitApp(RuleConstant.LIMIT_APP_DEFAULT);
    }

    public FlowRule(String resourceName) {
        super();
        setResource(resourceName);
        setLimitApp(RuleConstant.LIMIT_APP_DEFAULT);
    }

    /**
     * The threshold type of flow control (0: thread count, 1: QPS).
     */
    //限流阈值类型，QPS或并行占用线程数
    private int grade = RuleConstant.FLOW_GRADE_QPS;

    /**
     * Flow control threshold count.
     */
    //限流阈值
    private double count;

    /**
     * Flow control strategy based on invocation chain.
     *
     * {@link RuleConstant#STRATEGY_DIRECT} for direct flow control (by origin);
     * {@link RuleConstant#STRATEGY_RELATE} for relevant flow control (with relevant resource);
     * {@link RuleConstant#STRATEGY_CHAIN} for chain flow control (by entrance resource).
     */
    //基于调用关系的限流策略
    private int strategy = RuleConstant.STRATEGY_DIRECT;

    /**
     * Reference resource in flow control with relevant resource or context.
     */
    //引用的资源，仅在配置strategy的情况下使用
    private String refResource;

    /**
     * Rate limiter control behavior.
     * 0. default(reject directly), 1. warm up, 2. rate limiter, 3. warm up + rate limiter
     */
    //流量控制效果，如直接拒绝、冷启动、匀速排队
    private int controlBehavior = RuleConstant.CONTROL_BEHAVIOR_DEFAULT;
    //冷启动时长，单位为秒
    private int warmUpPeriodSec = 10;

    /**
     * Max queueing time in rate limiter behavior.
     */
    //最大排队等候时间，单位为毫秒
    private int maxQueueingTimeMs = 500;

    //是否集群限流
    private boolean clusterMode;
    /**
     * Flow rule config for cluster mode.
     */
    //集群配置
    private ClusterFlowConfig clusterConfig;

    /**
     * The traffic shaping (throttling) controller.
     */
    //流量效果控制器
    private TrafficShapingController controller;

    public int getControlBehavior() {
        return controlBehavior;
    }

    public FlowRule setControlBehavior(int controlBehavior) {
        this.controlBehavior = controlBehavior;
        return this;
    }

    public int getMaxQueueingTimeMs() {
        return maxQueueingTimeMs;
    }

    public FlowRule setMaxQueueingTimeMs(int maxQueueingTimeMs) {
        this.maxQueueingTimeMs = maxQueueingTimeMs;
        return this;
    }

    FlowRule setRater(TrafficShapingController rater) {
        this.controller = rater;
        return this;
    }

    TrafficShapingController getRater() {
        return controller;
    }

    public int getWarmUpPeriodSec() {
        return warmUpPeriodSec;
    }

    public FlowRule setWarmUpPeriodSec(int warmUpPeriodSec) {
        this.warmUpPeriodSec = warmUpPeriodSec;
        return this;
    }

    public int getGrade() {
        return grade;
    }

    public FlowRule setGrade(int grade) {
        this.grade = grade;
        return this;
    }

    public double getCount() {
        return count;
    }

    public FlowRule setCount(double count) {
        this.count = count;
        return this;
    }

    public int getStrategy() {
        return strategy;
    }

    public FlowRule setStrategy(int strategy) {
        this.strategy = strategy;
        return this;
    }

    public String getRefResource() {
        return refResource;
    }

    public FlowRule setRefResource(String refResource) {
        this.refResource = refResource;
        return this;
    }

    public boolean isClusterMode() {
        return clusterMode;
    }

    public FlowRule setClusterMode(boolean clusterMode) {
        this.clusterMode = clusterMode;
        return this;
    }

    public ClusterFlowConfig getClusterConfig() {
        return clusterConfig;
    }

    public FlowRule setClusterConfig(ClusterFlowConfig clusterConfig) {
        this.clusterConfig = clusterConfig;
        return this;
    }

    /**
     * Rule定义的行为应该只是Sentinel在最初搭建框架时定义的约定，Sentinel自己并没有完全遵守这个约定，由很多规则并没有将
     * passCheck方法交给Rule实现，而是交给了Checker实现，Checker是后续引入的，用于替代Rule的passCheck方法
     * @param context   当前调用链上下文
     * @param node  当前资源的DefaultNode实例
     * @param acquireCount
     * @param args  方法参数，用于实现热点参数限流降级
     * @return
     */
    @Override
    public boolean passCheck(Context context, DefaultNode node, int acquireCount, Object... args) {
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        if (!super.equals(o)) { return false; }

        FlowRule rule = (FlowRule)o;

        if (grade != rule.grade) { return false; }
        if (Double.compare(rule.count, count) != 0) { return false; }
        if (strategy != rule.strategy) { return false; }
        if (controlBehavior != rule.controlBehavior) { return false; }
        if (warmUpPeriodSec != rule.warmUpPeriodSec) { return false; }
        if (maxQueueingTimeMs != rule.maxQueueingTimeMs) { return false; }
        if (clusterMode != rule.clusterMode) { return false; }
        if (refResource != null ? !refResource.equals(rule.refResource) : rule.refResource != null) { return false; }
        return clusterConfig != null ? clusterConfig.equals(rule.clusterConfig) : rule.clusterConfig == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        long temp;
        result = 31 * result + grade;
        temp = Double.doubleToLongBits(count);
        result = 31 * result + (int)(temp ^ (temp >>> 32));
        result = 31 * result + strategy;
        result = 31 * result + (refResource != null ? refResource.hashCode() : 0);
        result = 31 * result + controlBehavior;
        result = 31 * result + warmUpPeriodSec;
        result = 31 * result + maxQueueingTimeMs;
        result = 31 * result + (clusterMode ? 1 : 0);
        result = 31 * result + (clusterConfig != null ? clusterConfig.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "FlowRule{" +
            "resource=" + getResource() +
            ", limitApp=" + getLimitApp() +
            ", grade=" + grade +
            ", count=" + count +
            ", strategy=" + strategy +
            ", refResource=" + refResource +
            ", controlBehavior=" + controlBehavior +
            ", warmUpPeriodSec=" + warmUpPeriodSec +
            ", maxQueueingTimeMs=" + maxQueueingTimeMs +
            ", clusterMode=" + clusterMode +
            ", clusterConfig=" + clusterConfig +
            ", controller=" + controller +
            '}';
    }
}
