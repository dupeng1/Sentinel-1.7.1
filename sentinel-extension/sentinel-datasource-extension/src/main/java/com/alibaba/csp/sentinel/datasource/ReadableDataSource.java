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
package com.alibaba.csp.sentinel.datasource;

import com.alibaba.csp.sentinel.property.SentinelProperty;

/**
 * The readable data source is responsible for retrieving configs (read-only).
 *
 * @param <S> source data type
 * @param <T> target data type
 * @author leyou
 * @author Eric Zhao
 */

/**
 * 动态数据源，
 * 1、可以调用getProperty方法获取动态数据源提供的SentinelProperty实例
 * 2、并将此SentinelProperty实例注册给规则管理器
 * 3、这样动态数据源在读取到配置时就可以调用自身SentinelProperty实例的updateValue方法通知规则管理器更新规则
 * @param <S>   代表用于装在从数据源中读取的配置的类型
 * @param <T>   代表对应Sentinel中的规则类型
 */
public interface ReadableDataSource<S, T> {

    /**
     * Load data data source as the target type.
     *
     * @return the target data.
     * @throws Exception IO or other error occurs
     */
    //加载配置
    T loadConfig() throws Exception;

    /**
     * Read original data from the data source.
     *
     * @return the original data.
     * @throws Exception IO or other error occurs
     */
    //从数据源中读取配置，数据源既可以是yaml配置文件，也可以是MySQL、Redis，由实现类决定从哪种数据源中读取配置
    S readSource() throws Exception;

    /**
     * Get {@link SentinelProperty} of the data source.
     *
     * @return the property.
     */
    //获取SentinelProperty实例
    SentinelProperty<T> getProperty();

    /**
     * Close the data source.
     *
     * @throws Exception IO or other error occurs
     */
    //用于关闭数据源
    void close() throws Exception;
}
