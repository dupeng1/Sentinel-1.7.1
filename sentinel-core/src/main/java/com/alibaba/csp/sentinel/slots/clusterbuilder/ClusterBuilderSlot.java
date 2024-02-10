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
package com.alibaba.csp.sentinel.slots.clusterbuilder;

import java.util.HashMap;
import java.util.Map;

import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.context.ContextUtil;
import com.alibaba.csp.sentinel.node.ClusterNode;
import com.alibaba.csp.sentinel.node.DefaultNode;
import com.alibaba.csp.sentinel.node.IntervalProperty;
import com.alibaba.csp.sentinel.node.Node;
import com.alibaba.csp.sentinel.node.SampleCountProperty;
import com.alibaba.csp.sentinel.slotchain.AbstractLinkedProcessorSlot;
import com.alibaba.csp.sentinel.slotchain.ProcessorSlotChain;
import com.alibaba.csp.sentinel.slotchain.ResourceWrapper;
import com.alibaba.csp.sentinel.slotchain.StringResourceWrapper;

/**
 * <p>
 * This slot maintains resource running statistics (response time, qps, thread
 * count, exception), and a list of callers as well which is marked by
 * {@link ContextUtil#enter(String origin)}
 * </p>
 * <p>
 * One resource has only one cluster node, while one resource can have multiple
 * default nodes.
 * </p>
 *
 * @author jialiang.linjl
 */

/**
 * 1、为调用链上的资源创建ClusterNode实例，以及对于不同调用来源，为调用链上的资源都会创建一个StatisticNode实例
 * 2、ClusterBuilderSlot在创建ClusterNode实例时，需要将ClusterNode实例赋值给DefaultNode实例的clusterNode字段，由DefaultNode实例
 * 持有ClusterNode实例，并由DefaultNode负责代理ClusterNode完成资源指标数据统计
 * 3、必须先有DefaultNode实例，才能将ClusterNode实例委托给DefaultNode实例，这就是ClusterBuilderSlot在NodeSelectorSlot之后的原因
 */
public class ClusterBuilderSlot extends AbstractLinkedProcessorSlot<DefaultNode> {

    /**
     * <p>
     * Remember that same resource({@link ResourceWrapper#equals(Object)}) will share
     * the same {@link ProcessorSlotChain} globally, no matter in witch context. So if
     * code goes into {@link #entry(Context, ResourceWrapper, DefaultNode, int, boolean, Object...)},
     * the resource name must be same but context name may not.
     * </p>
     * <p>
     * To get total statistics of the same resource in different context, same resource
     * shares the same {@link ClusterNode} globally. All {@link ClusterNode}s are cached
     * in this map.
     * </p>
     * <p>
     * The longer the application runs, the more stable this mapping will
     * become. so we don't concurrent map but a lock. as this lock only happens
     * at the very beginning while concurrent map will hold the lock all the time.
     * </p>
     */
    //缓存不同资源的全局唯一ClusterNode
    private static volatile Map<ResourceWrapper, ClusterNode> clusterNodeMap = new HashMap<>();

    private static final Object lock = new Object();
    //非静态字段，持有当前资源的ClusterNode，省去每次都从clusterNodeMap静态字段中获取资源的ClusterNode实例的步骤
    private volatile ClusterNode clusterNode = null;

    @Override
    public void entry(Context context, ResourceWrapper resourceWrapper, DefaultNode node, int count,
                      boolean prioritized, Object... args)
        throws Throwable {
        //如果clusterNode字段为空，说明当前资源首次被访问，那么需要为资源创建一个全局唯一的clusterNode实例
        if (clusterNode == null) {
            synchronized (lock) {
                if (clusterNode == null) {
                    // Create the cluster node.
                    clusterNode = new ClusterNode(resourceWrapper.getName(), resourceWrapper.getResourceType());
                    HashMap<ResourceWrapper, ClusterNode> newMap = new HashMap<>(Math.max(clusterNodeMap.size(), 16));
                    newMap.putAll(clusterNodeMap);
                    newMap.put(node.getId(), clusterNode);

                    clusterNodeMap = newMap;
                }
            }
        }
        //将ClusterNode实例委托给资源的DefaultNode实例来统计资源指标数据
        node.setClusterNode(clusterNode);

        /*
         * if context origin is set, we should get or create a new {@link Node} of
         * the specific origin.
         */
        if (!"".equals(context.getOrigin())) {
            //如果调用来源不空，那么为当前调用来源创建一个StatisticNode实例
            Node originNode = node.getClusterNode().getOrCreateOriginNode(context.getOrigin());
            context.getCurEntry().setOriginNode(originNode);
        }

        fireEntry(context, resourceWrapper, node, count, prioritized, args);
    }

    @Override
    public void exit(Context context, ResourceWrapper resourceWrapper, int count, Object... args) {
        fireExit(context, resourceWrapper, count, args);
    }

    /**
     * Get {@link ClusterNode} of the resource of the specific type.
     *
     * @param id   resource name.
     * @param type invoke type.
     * @return the {@link ClusterNode}
     */
    public static ClusterNode getClusterNode(String id, EntryType type) {
        return clusterNodeMap.get(new StringResourceWrapper(id, type));
    }

    /**
     * Get {@link ClusterNode} of the resource name.
     *
     * @param id resource name.
     * @return the {@link ClusterNode}.
     */
    public static ClusterNode getClusterNode(String id) {
        if (id == null) {
            return null;
        }
        ClusterNode clusterNode = null;

        for (EntryType nodeType : EntryType.values()) {
            clusterNode = clusterNodeMap.get(new StringResourceWrapper(id, nodeType));
            if (clusterNode != null) {
                break;
            }
        }

        return clusterNode;
    }

    /**
     * Get {@link ClusterNode}s map, this map holds all {@link ClusterNode}s, it's key is resource name,
     * value is the related {@link ClusterNode}. <br/>
     * DO NOT MODIFY the map returned.
     *
     * @return all {@link ClusterNode}s
     */
    public static Map<ResourceWrapper, ClusterNode> getClusterNodeMap() {
        return clusterNodeMap;
    }

    /**
     * Reset all {@link ClusterNode}s. Reset is needed when {@link IntervalProperty#INTERVAL} or
     * {@link SampleCountProperty#SAMPLE_COUNT} is changed.
     */
    public static void resetClusterNodes() {
        for (ClusterNode node : clusterNodeMap.values()) {
            node.reset();
        }
    }
}
