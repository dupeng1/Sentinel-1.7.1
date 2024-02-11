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
package com.alibaba.csp.sentinel.adapter.spring.webmvc.config;

import com.alibaba.csp.sentinel.adapter.spring.webmvc.callback.UrlCleaner;

/**
 * @author kaizi2009
 * @since 1.7.1
 */
public class SentinelWebMvcConfig extends BaseWebMvcConfig {

    public static final String DEFAULT_REQUEST_ATTRIBUTE_NAME = "$$sentinel_spring_web_entry_attr";

    /**
     * Specify the URL cleaner that unifies the URL resources.
     */
    private UrlCleaner urlCleaner;
    /**
     * Specify whether the URL resource name should contain the HTTP method prefix (e.g. {@code POST:}).
     */
    private boolean httpMethodSpecify;

    public SentinelWebMvcConfig() {
        super();
        setRequestAttributeName(DEFAULT_REQUEST_ATTRIBUTE_NAME);
    }

    public UrlCleaner getUrlCleaner() {
        return urlCleaner;
    }

    public SentinelWebMvcConfig setUrlCleaner(UrlCleaner urlCleaner) {
        this.urlCleaner = urlCleaner;
        return this;
    }

    public boolean isHttpMethodSpecify() {
        return httpMethodSpecify;
    }

    /**
     * 是否需要给资源名称加上HttpMethod前缀，例如，对于 GET 接口/hello，如果将 httpMethodSpecify 配置为 false，则资源名称为/hello，否则资源名称
     * 为 GET:/hello
     * @param httpMethodSpecify
     * @return
     */
    public SentinelWebMvcConfig setHttpMethodSpecify(boolean httpMethodSpecify) {
        this.httpMethodSpecify = httpMethodSpecify;
        return this;
    }

    @Override
    public String toString() {
        return "SentinelWebMvcConfig{" +
            "urlCleaner=" + urlCleaner +
            ", httpMethodSpecify=" + httpMethodSpecify +
            ", requestAttributeName='" + requestAttributeName + '\'' +
            ", blockExceptionHandler=" + blockExceptionHandler +
            ", originParser=" + originParser +
            '}';
    }
}
