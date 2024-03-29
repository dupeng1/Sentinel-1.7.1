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
package com.alibaba.csp.sentinel.cluster.server;

import com.alibaba.csp.sentinel.cluster.TokenService;

/**
 * Embedded token server interface that can work in embedded mode.
 *
 * @author Eric Zhao
 * @since 1.4.0
 */

/**
 * 支持嵌入式模式的集群限流服务端需要实现的接口
 * 整合了集群限流客户端和集群限流服务端的功能，为嵌入式模式提供支持，在嵌入式模式下，如果当前节点是集群限流服务端，就没有必要发起网络请求
 */
public interface EmbeddedClusterTokenServer extends ClusterTokenServer, TokenService {
}
