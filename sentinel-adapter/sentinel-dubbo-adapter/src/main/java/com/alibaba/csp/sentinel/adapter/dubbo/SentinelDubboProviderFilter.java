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
package com.alibaba.csp.sentinel.adapter.dubbo;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.ResourceTypeConstants;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.adapter.dubbo.config.DubboConfig;
import com.alibaba.csp.sentinel.adapter.dubbo.fallback.DubboFallbackRegistry;
import com.alibaba.csp.sentinel.context.ContextUtil;
import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.dubbo.common.extension.Activate;
import com.alibaba.dubbo.rpc.Filter;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcException;

/**
 * <p>Dubbo service provider filter for Sentinel. Auto activated by default.</p>
 *
 * If you want to disable the provider filter, you can configure:
 * <pre>
 * &lt;dubbo:provider filter="-sentinel.dubbo.provider.filter"/&gt;
 * </pre>
 *
 * @author leyou
 * @author Eric Zhao
 */
/**
 * Sentinel适配Dubbo框架的过滤器，只在服务端生效
 */
@Activate(group = "provider")
public class SentinelDubboProviderFilter extends AbstractDubboFilter implements Filter {

    public SentinelDubboProviderFilter() {
        RecordLog.info("Sentinel Dubbo provider filter initialized");
    }

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        // Get origin caller.
        //1、获取调用来源
        String application = DubboUtils.getApplication(invocation, "");

        Entry interfaceEntry = null;
        Entry methodEntry = null;
        try {
            //2、生成不同粒度的资源名称
            String resourceName = getResourceName(invoker, invocation, DubboConfig.getDubboProviderPrefix());
            String interfaceName = invoker.getInterface().getName();
            //3、调用链入口名称为方法级别的资源名称
            ContextUtil.enter(resourceName, application);
            //4、为不同粒度资源调用SphU.entry方法
            interfaceEntry = SphU.entry(interfaceName, ResourceTypeConstants.COMMON_RPC, EntryType.IN);
            methodEntry = SphU.entry(resourceName, ResourceTypeConstants.COMMON_RPC,
                EntryType.IN, invocation.getArguments());

            Result result = invoker.invoke(invocation);
            if (result.hasException()) {
                //5、当远程响应异常时，为不同粒度资源统计异常指标
                Throwable e = result.getException();
                // Record common exception.
                Tracer.traceEntry(e, interfaceEntry);
                Tracer.traceEntry(e, methodEntry);
            }
            return result;
        } catch (BlockException e) {
            //6、如果抛出BlockException，则说明当前请求被拒绝，可调用注册的全局Fallback处理器完成降级逻辑处理
            return DubboFallbackRegistry.getProviderFallback().handle(invoker, invocation, e);
        } catch (RpcException e) {
            //7、当发起调用抛出异常时，为不同粒度资源分别统计异常指标
            Tracer.traceEntry(e, interfaceEntry);
            Tracer.traceEntry(e, methodEntry);
            throw e;
        } finally {
            //8、为不同粒度资源分别调用SphU.exit方法
            if (methodEntry != null) {
                methodEntry.exit(1, invocation.getArguments());
            }
            if (interfaceEntry != null) {
                interfaceEntry.exit();
            }
            //9、调用ContextUtil#exit方法
            ContextUtil.exit();
        }
    }
}
