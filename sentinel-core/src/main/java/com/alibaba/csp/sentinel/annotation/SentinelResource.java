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
package com.alibaba.csp.sentinel.annotation;

import com.alibaba.csp.sentinel.EntryType;

import java.lang.annotation.*;

/**
 * The annotation indicates a definition of Sentinel resource.
 *
 * @author Eric Zhao
 * @since 0.1.1
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
/**
 * 注解用于定义资源，作为切点，可被注释在方法或类上
 */
public @interface SentinelResource {

    /**
     * @return name of the Sentinel resource
     */
    //配置资源名称
    String value() default "";

    /**
     * @return the entry type (inbound or outbound), outbound by default
     */
    //配置流量类型（IN/OUT）
    EntryType entryType() default EntryType.OUT;

    /**
     * @return the classification (type) of the resource
     * @since 1.7.0
     */
    //配置资源类型，如COMMON_WEB、COMMON_RPC
    int resourceType() default 0;

    /**
     * @return name of the block exception function, empty by default
     */
    //需要与blockHandlerClass组合使用，配置BlockException处理器的方法名称
    String blockHandler() default "";

    /**
     * The {@code blockHandler} is located in the same class with the original method by default.
     * However, if some methods share the same signature and intend to set the same block handler,
     * then users can set the class where the block handler exists. Note that the block handler method
     * must be static.
     *
     * @return the class where the block handler exists, should not provide more than one classes
     */
    //配置BlockException处理器的类型
    Class<?>[] blockHandlerClass() default {};

    /**
     * @return name of the fallback function, empty by default
     */
    //配置Fallback方法名称，需要与fallbackClass组合使用，提供类似与适配OpenFeign框架的失败回退机制，可用于限流、熔断后的降级处理
    String fallback() default "";

    /**
     * The {@code defaultFallback} is used as the default universal fallback method.
     * It should not accept any parameters, and the return type should be compatible
     * with the original method.
     *
     * @return name of the default fallback method, empty by default
     * @since 1.6.0
     */
    String defaultFallback() default "";

    /**
     * The {@code fallback} is located in the same class with the original method by default.
     * However, if some methods share the same signature and intend to set the same fallback,
     * then users can set the class where the fallback function exists. Note that the shared fallback method
     * must be static.
     *
     * @return the class where the fallback method is located (only single class)
     * @since 1.6.0
     */
    //配置Fallback处理器的类型
    Class<?>[] fallbackClass() default {};

    /**
     * @return the list of exception classes to trace, {@link Throwable} by default
     * @since 1.5.1
     */
    //指定只跟踪哪些类型的异常，不配置会导致Sentinel不统计异常指标数据，默认追踪所有类型的异常，会被Sentinel统计到异常指标，并且调用Fallback处理器处理降级
    Class<? extends Throwable>[] exceptionsToTrace() default {Throwable.class};
    
    /**
     * Indicates the exceptions to be ignored. Note that {@code exceptionsToTrace} should
     * not appear with {@code exceptionsToIgnore} at the same time, or {@code exceptionsToIgnore}
     * will be of higher precedence.
     *
     * @return the list of exception classes to ignore, empty by default
     * @since 1.6.0
     */
    //与exceptionsToTrace正好相反，指定不跟踪哪些类型的异常，不仅不会被Sentinel统计到异常指标中，也不会调用Fallback处理器处理降级
    Class<? extends Throwable>[] exceptionsToIgnore() default {};
}
