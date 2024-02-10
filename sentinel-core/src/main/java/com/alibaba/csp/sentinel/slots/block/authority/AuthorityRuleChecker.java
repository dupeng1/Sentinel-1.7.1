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
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.util.StringUtil;

/**
 * Rule checker for white/black list authority.
 *
 * @author Eric Zhao
 * @since 0.2.0
 */

/**
 * 授权检查类，负责实现黑白名单的过滤逻辑
 */
final class AuthorityRuleChecker {

    static boolean passCheck(AuthorityRule rule, Context context) {
        //获取调用来源
        String requester = context.getOrigin();

        // Empty origin or empty limitApp will pass.
        //若调用来源为空或规则没有配置黑白名单，则放行请求
        if (StringUtil.isEmpty(requester) || StringUtil.isEmpty(rule.getLimitApp())) {
            return true;
        }

        // Do exact match with origin name.
        //查找字符串，这一步具有快速过滤的作用，可以提升性能
        int pos = rule.getLimitApp().indexOf(requester);
        boolean contain = pos > -1;
        //若存在，则精确匹配
        if (contain) {
            boolean exactlyMatch = false;
            //分割数组
            String[] appArray = rule.getLimitApp().split(",");
            for (String app : appArray) {
                //将标志设置为true
                if (requester.equals(app)) {
                    exactlyMatch = true;
                    break;
                }
            }

            contain = exactlyMatch;
        }
        //策略
        int strategy = rule.getStrategy();
        //如果是黑名单，且调用来源在规则配置的黑名单中
        if (strategy == RuleConstant.AUTHORITY_BLACK && contain) {
            return false;
        }
        //如果是白名单，且调用来源不在规则配置的白名单中
        if (strategy == RuleConstant.AUTHORITY_WHITE && !contain) {
            return false;
        }

        return true;
    }

    private AuthorityRuleChecker() {}
}
