/*
 * Copyright 1999-2019 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.csp.sentinel;

/**
 * @author Eric Zhao
 * @since 1.7.0
 */

/**
 * 资源类型
 */
public final class ResourceTypeConstants {
    //默认，可以是接口、一个方法、一段代码
    public static final int COMMON = 0;
    //Web 应用的接口
    public static final int COMMON_WEB = 1;
    //使用 Dubbo 框架实现的 RPC 接口
    public static final int COMMON_RPC = 2;
    //API Gateway 网关接口
    public static final int COMMON_API_GATEWAY = 3;
    //数据库 SQL 操作
    public static final int COMMON_DB_SQL = 4;

    private ResourceTypeConstants() {}
}
