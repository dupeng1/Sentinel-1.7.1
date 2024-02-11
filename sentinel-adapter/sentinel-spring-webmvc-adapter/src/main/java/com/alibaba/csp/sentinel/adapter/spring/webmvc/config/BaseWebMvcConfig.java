/*
 * Copyright 1999-2019 Alibaba Group Holding Ltd.
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
package com.alibaba.csp.sentinel.adapter.spring.webmvc.config;

import com.alibaba.csp.sentinel.adapter.spring.webmvc.callback.BlockExceptionHandler;
import com.alibaba.csp.sentinel.adapter.spring.webmvc.callback.RequestOriginParser;
import com.alibaba.csp.sentinel.util.AssertUtil;

/**
 * Common base configuration for Spring Web MVC adapter.
 *
 * @author kaizi2009
 * @since 1.7.1
 */
public abstract class BaseWebMvcConfig {

    protected String requestAttributeName;
    protected BlockExceptionHandler blockExceptionHandler;
    protected RequestOriginParser originParser;

    //设置entry在request的属性名
    public String getRequestAttributeName() {
        return requestAttributeName;
    }

    public void setRequestAttributeName(String requestAttributeName) {
        this.requestAttributeName = requestAttributeName;
    }

    public BlockExceptionHandler getBlockExceptionHandler() {
        return blockExceptionHandler;
    }

    //设置BlockException处理器，如果不想配置BlockException处理器，则可以在Spring MVC的全局异常处理器中处理BlockException
    public void setBlockExceptionHandler(BlockExceptionHandler blockExceptionHandler) {
        this.blockExceptionHandler = blockExceptionHandler;
    }

    public RequestOriginParser getOriginParser() {
        return originParser;
    }

    //注册调用来源解析器，从请求头中获取S-user参数的值作为调用来源名称，在向下游服务发起请求时在请求头写入S-user参数
    public void setOriginParser(RequestOriginParser originParser) {
        this.originParser = originParser;
    }
}
