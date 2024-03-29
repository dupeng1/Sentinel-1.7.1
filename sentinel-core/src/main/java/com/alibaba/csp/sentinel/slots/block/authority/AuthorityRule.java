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
package com.alibaba.csp.sentinel.slots.block.authority;

import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.node.DefaultNode;
import com.alibaba.csp.sentinel.slots.block.AbstractRule;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;

/**
 * Authority rule is designed for limiting by request origins.
 *
 * @author youji.zj
 */

/**
 * 授权规则
 */
public class AuthorityRule extends AbstractRule {

    /**
     * Mode: 0 for whitelist; 1 for blacklist.
     */
    /**
     * 限流策略，白名单或黑名单
     * 1、当配置为白名单时，limitApp就是调用来源白名单
     * 2、当配置为黑名单时，limitApp就是调用来源黑名单
     */
    private int strategy = RuleConstant.AUTHORITY_WHITE;

    public int getStrategy() {
        return strategy;
    }

    public AuthorityRule setStrategy(int strategy) {
        this.strategy = strategy;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (!(o instanceof AuthorityRule)) { return false; }
        if (!super.equals(o)) { return false; }

        AuthorityRule rule = (AuthorityRule)o;

        return strategy == rule.strategy;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + strategy;
        return result;
    }

    @Override
    public boolean passCheck(Context context, DefaultNode node, int count, Object... args) {
        return true;
    }

    @Override
    public String toString() {
        return "AuthorityRule{" +
            "resource=" + getResource() +
            ", limitApp=" + getLimitApp() +
            ", strategy=" + strategy +
            "} ";
    }
}
