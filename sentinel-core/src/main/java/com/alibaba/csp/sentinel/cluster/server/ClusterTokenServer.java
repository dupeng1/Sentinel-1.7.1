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

/**
 * Token server interface for distributed flow control.
 *
 * @author Eric Zhao
 * @since 1.4.0
 */

/**
 * 独立应用模式下，需要手动创建ClusterTokenServer并启动，再启动之前需要指定监听端口和连接最大空闲等待时间等配置
 * 集群限流服务端需要实现的接口
 */
public interface ClusterTokenServer {

    /**
     * Start the Sentinel cluster server.
     *
     * @throws Exception if any error occurs
     */
    //启动集群限流服务端，负责启动能够接收和响应客户端请求的网络通信服务端，根据接收的消息类型处理客户端请求
    void start() throws Exception;

    /**
     * Stop the Sentinel cluster server.
     *
     * @throws Exception if any error occurs
     */
    //停止集群限流服务端
    void stop() throws Exception;
}
