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

import com.alibaba.csp.sentinel.property.DynamicSentinelProperty;
import com.alibaba.csp.sentinel.property.SentinelProperty;

/**
 * The abstract readable data source provides basic functionality for loading and parsing config.
 *
 * @param <S> source data type
 * @param <T> target data type
 * @author Carpenter Lee
 * @author Eric Zhao
 */
public abstract class AbstractDataSource<S, T> implements ReadableDataSource<S, T> {

    //要求所有子类必须提供一个数据转换器，用于将S类型的实例转换为T类型的实例
    protected final Converter<S, T> parser;
    //在构造方法中创建DynamicSentinelProperty实例，因此子类无需创建SentinelProperty实例
    protected final SentinelProperty<T> property;

    public AbstractDataSource(Converter<S, T> parser) {
        if (parser == null) {
            throw new IllegalArgumentException("parser can't be null");
        }
        this.parser = parser;
        this.property = new DynamicSentinelProperty<T>();
    }

    @Override
    public T loadConfig() throws Exception {
        //调用子类实现的readSource()方法从数据源中读取配置，返回的实例类型为S
        return loadConfig(readSource());
    }

    public T loadConfig(S conf) throws Exception {
        //调用Converter实例的convert方法，将S类型的实例转换为T类型的实例
        T value = parser.convert(conf);
        return value;
    }

    @Override
    public SentinelProperty<T> getProperty() {
        return property;
    }
}
