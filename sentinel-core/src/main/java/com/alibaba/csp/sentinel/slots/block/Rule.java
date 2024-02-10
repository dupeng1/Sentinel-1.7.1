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
package com.alibaba.csp.sentinel.slots.block;

import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.node.DefaultNode;

/**
 * Base interface of all rules.
 *
 * @author youji.zj
 */
public interface Rule {

    /**
     * Check whether current statistical indicators meet this rule, which means not exceeding any threshold.
     *
     * @param context current {@link Context}
     * @param node    current {@link com.alibaba.csp.sentinel.node.Node}
     * @param count   tokens needed.
     * @param args    arguments of the original invocation.
     * @return If current statistical indicators not exceeding any threshold return true, otherwise return false.
     */
    /**
     * 判断当前请求是否被允许通过
     * @param context   当前调用链上下文
     * @param node  当前资源的DefaultNode实例
     * @param count 一般为1，用在令牌桶算法中表示需要申请的令牌数；用在QPS统计中表示一个请求；用在并行占用线程数统计中表示一个线程
     * @param args  方法参数，用于实现热点参数限流降级
     * @return
     */
    boolean passCheck(Context context, DefaultNode node, int count, Object... args);

}
