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
package com.alibaba.csp.sentinel.slots.statistic;

/**
 * @author Eric Zhao
 */

/**
 * 定义了Sentinel会收集的指标数据
 */
public enum MetricEvent {

    /**
     * Normal pass.
     */
    //pass指标：请求被放行的总数
    PASS,
    /**
     * Normal block.
     */
    //block指标：请求被拒绝的总数
    BLOCK,
    //exception指标：异常的请求总数
    EXCEPTION,
    //success指标：被成功处理的请求总数
    SUCCESS,
    //rt指标：被成功处理的请求的总耗时
    RT,

    /**
     * Passed in future quota (pre-occupied, since 1.5.0).
     */
    //occupied_pass指标：预通过总数
    OCCUPIED_PASS
}
