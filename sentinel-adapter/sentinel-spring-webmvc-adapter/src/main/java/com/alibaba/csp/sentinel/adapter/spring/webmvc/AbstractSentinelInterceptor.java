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
package com.alibaba.csp.sentinel.adapter.spring.webmvc;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.ResourceTypeConstants;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.adapter.spring.webmvc.config.BaseWebMvcConfig;
import com.alibaba.csp.sentinel.context.ContextUtil;
import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.util.AssertUtil;
import com.alibaba.csp.sentinel.util.StringUtil;

import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * @author kaizi2009
 * @since 1.7.1
 */

/**
 * 1、HandlerInterceptor在DispatcherServlet#doDispatch 方法中被调用，每个方法的调用时机如下
 * preHandle：在调用接口方法之前被调用
 * postHandle：在接口方法执行完成并返回ModelAndView时被调用
 * afterCompletion：在接口方法执行完成时被调用，无论执行成功或发生异常都会被调用
 * 2、在HandlerInterceptor#preHandle方法中调用ContextUtil#enter方法、SphU#entry方法，在afterCompletion方法中根据方法参数ex是否为空来处理异常情况，
 * 并且完成 Entry#exit方法及ContextUtil#exit方法的调用
 */
public abstract class AbstractSentinelInterceptor implements HandlerInterceptor {

    public static final String SENTINEL_SPRING_WEB_CONTEXT_NAME = "sentinel_spring_web_context";
    private static final String EMPTY_ORIGIN = "";

    private final BaseWebMvcConfig baseWebMvcConfig;

    public AbstractSentinelInterceptor(BaseWebMvcConfig config) {
        AssertUtil.notNull(config, "BaseWebMvcConfig should not be null");
        AssertUtil.assertNotBlank(config.getRequestAttributeName(), "requestAttributeName should not be blank");
        this.baseWebMvcConfig = config;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
        throws Exception {
        try {
            //1、获取资源名称
            String resourceName = getResourceName(request);

            if (StringUtil.isNotEmpty(resourceName)) {
                // Parse the request origin using registered origin parser.
                //2、解析调用来源，如从请求头中获取S-user参数的值
                String origin = parseOrigin(request);
                //3、调用ContextUtil.enter方法，调用链入口名称为sentinel_spring_web_context
                ContextUtil.enter(SENTINEL_SPRING_WEB_CONTEXT_NAME, origin);
                //4、调用SphU#entry方法，资源类型为COMMON_WEB，流量类型为IN
                Entry entry = SphU.entry(resourceName, ResourceTypeConstants.COMMON_WEB, EntryType.IN);
                //5、将 SphU#entry 方法返回的 Entry 实例放入 HttpServletRequest 参数的属性表中，
                // 方便在AbstractSentinelInterceptor#afterCompletion方法处理BlockException
                setEntryInRequest(request, baseWebMvcConfig.getRequestAttributeName(), entry);
            }
            return true;
        } catch (BlockException e) {
            //6、如果抛出BlockException，则说明当前请求被拒绝，需要调用handleBlockException方法处理BlockException
            handleBlockException(request, response, e);
            return false;
        }
    }

    /**
     * Return the resource name of the target web resource.
     *
     * @param request web request
     * @return the resource name of the target web resource.
     */
    protected abstract String getResourceName(HttpServletRequest request);

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) throws Exception {
        //1、从HttpServletRequest参数的属性表中获取preHandle方法中的Entry实例
        Entry entry = getEntryInRequest(request, baseWebMvcConfig.getRequestAttributeName());
        if (entry != null) {
            //2、调用AbstractSentinelInterceptor#traceExceptionAndExit方法
            traceExceptionAndExit(entry, ex);
            removeEntryInRequest(request);
        }
        //3、调用ContextUtil#exit方法
        ContextUtil.exit();
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           ModelAndView modelAndView) throws Exception {
    }

    protected void setEntryInRequest(HttpServletRequest request, String name, Entry entry) {
        Object attrVal = request.getAttribute(name);
        if (attrVal != null) {
            RecordLog.warn("[{}] The attribute key '{0}' already exists in request, please set `requestAttributeName`",
                getClass().getSimpleName(), name);
        } else {
            request.setAttribute(name, entry);
        }
    }

    protected Entry getEntryInRequest(HttpServletRequest request, String attrKey) {
        Object entryObject = request.getAttribute(attrKey);
        return entryObject == null ? null : (Entry)entryObject;
    }

    protected void removeEntryInRequest(HttpServletRequest request) {
        request.removeAttribute(baseWebMvcConfig.getRequestAttributeName());
    }

    //当方法执行抛出异常时，调用Tracer#traceEntry方法统计异常指标数据
    protected void traceExceptionAndExit(Entry entry, Exception ex) {
        if (entry != null) {
            if (ex != null) {
                Tracer.traceEntry(ex, entry);
            }
            entry.exit();
        }
    }

    protected void handleBlockException(HttpServletRequest request, HttpServletResponse response, BlockException e)
        throws Exception {
        //若SentinelWebMvcConfig配置了BlockExceptionHandler，则调用BlockExceptionHandler#handle方法处理BlockException，
        //否则将抛出BlockException，并由全局处理器处理
        if (baseWebMvcConfig.getBlockExceptionHandler() != null) {
            baseWebMvcConfig.getBlockExceptionHandler().handle(request, response, e);
        } else {
            // Throw BlockException directly. Users need to handle it in Spring global exception handler.
            throw e;
        }
    }

    protected String parseOrigin(HttpServletRequest request) {
        String origin = EMPTY_ORIGIN;
        if (baseWebMvcConfig.getOriginParser() != null) {
            origin = baseWebMvcConfig.getOriginParser().parseOrigin(request);
            if (StringUtil.isEmpty(origin)) {
                return EMPTY_ORIGIN;
            }
        }
        return origin;
    }

}
