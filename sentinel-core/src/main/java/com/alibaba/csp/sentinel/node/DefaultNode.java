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
package com.alibaba.csp.sentinel.node;

import java.util.HashSet;
import java.util.Set;

import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.SphO;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.slotchain.ResourceWrapper;
import com.alibaba.csp.sentinel.slots.nodeselector.NodeSelectorSlot;

/**
 * <p>
 * A {@link Node} used to hold statistics for specific resource name in the specific context.
 * Each distinct resource in each distinct {@link Context} will corresponding to a {@link DefaultNode}.
 * </p>
 * <p>
 * This class may have a list of sub {@link DefaultNode}s. Child nodes will be created when
 * calling {@link SphU}#entry() or {@link SphO}@entry() multiple times in the same {@link Context}.
 * </p>
 *
 * @author qinan.qn
 * @see NodeSelectorSlot
 */

/**
 * 1、统计同一资源、不同调用链入口的实时指标数据，是StatisticNode类的子类，其构造方法要求传入资源ID，表示该DefaultNode实例
 * 用于统计该资源的实时指标数据。<br>
 * 2、一个资源可能有多个DefaultNode实例，而是否有多个DefaultNode实例取决于该资源是否被多个不同入口节点的调用链包含。这样可
 * 按不同调用链入口对资源采取不同的流量控制策略<br>
 * ContextUtil.enter("myContextt");<br>
 * entry = SphU.entry("demo", EntryType.IN);<br>
 * doBusiness();<br>
 * ContextUtil.exit();<br>
 * doBusiness方法就是被Sentinel保护的资源，资源名称为demo，调用链入口名称为myContextt。
 * 如果存在另一个入口名称为myContextt2的调用链也使用资源demo，那么Sentinel就会对同一个资源demo创建两个DefaultNode实例
 */
public class DefaultNode extends StatisticNode {

    /**
     * The resource associated with the node.
     */
    //资源ID
    private ResourceWrapper id;

    /**
     * The list of all child nodes.
     */
    //存放子节点和构造调用树
    private volatile Set<Node> childList = new HashSet<>();

    /**
     * Associated cluster node.
     */
    //引用当前资源全局唯一的ClusterNode实例
    private ClusterNode clusterNode;

    public DefaultNode(ResourceWrapper id, ClusterNode clusterNode) {
        this.id = id;
        this.clusterNode = clusterNode;
    }

    public ResourceWrapper getId() {
        return id;
    }

    public ClusterNode getClusterNode() {
        return clusterNode;
    }

    public void setClusterNode(ClusterNode clusterNode) {
        this.clusterNode = clusterNode;
    }

    /**
     * Add child node to current node.
     *
     * @param node valid child node
     */
    public void addChild(Node node) {
        if (node == null) {
            RecordLog.warn("Trying to add null child to node <{0}>, ignored", id.getName());
            return;
        }
        if (!childList.contains(node)) {
            synchronized (this) {
                if (!childList.contains(node)) {
                    Set<Node> newSet = new HashSet<>(childList.size() + 1);
                    newSet.addAll(childList);
                    newSet.add(node);
                    childList = newSet;
                }
            }
            RecordLog.info("Add child <{0}> to node <{1}>", ((DefaultNode)node).id.getName(), id.getName());
        }
    }

    /**
     * Reset the child node list.
     */
    public void removeChildList() {
        this.childList = new HashSet<>();
    }

    public Set<Node> getChildList() {
        return childList;
    }

    @Override
    public void increaseBlockQps(int count) {
        super.increaseBlockQps(count);
        this.clusterNode.increaseBlockQps(count);
    }

    @Override
    public void increaseExceptionQps(int count) {
        super.increaseExceptionQps(count);
        this.clusterNode.increaseExceptionQps(count);
    }

    @Override
    public void addRtAndSuccess(long rt, int successCount) {
        super.addRtAndSuccess(rt, successCount);
        this.clusterNode.addRtAndSuccess(rt, successCount);
    }

    @Override
    public void increaseThreadNum() {
        super.increaseThreadNum();
        this.clusterNode.increaseThreadNum();
    }

    @Override
    public void decreaseThreadNum() {
        super.decreaseThreadNum();
        this.clusterNode.decreaseThreadNum();
    }

    @Override
    public void addPassRequest(int count) {
        super.addPassRequest(count);
        //调用被委托者（ClusterNode）的相同方法
        this.clusterNode.addPassRequest(count);
    }

    public void printDefaultNode() {
        visitTree(0, this);
    }

    private void visitTree(int level, DefaultNode node) {
        for (int i = 0; i < level; ++i) {
            System.out.print("-");
        }
        if (!(node instanceof EntranceNode)) {
            System.out.println(
                String.format("%s(thread:%s pq:%s bq:%s tq:%s rt:%s 1mp:%s 1mb:%s 1mt:%s)", node.id.getShowName(),
                    node.curThreadNum(), node.passQps(), node.blockQps(), node.totalQps(), node.avgRt(),
                    node.totalRequest() - node.blockRequest(), node.blockRequest(), node.totalRequest()));
        } else {
            System.out.println(
                String.format("Entry-%s(t:%s pq:%s bq:%s tq:%s rt:%s 1mp:%s 1mb:%s 1mt:%s)", node.id.getShowName(),
                    node.curThreadNum(), node.passQps(), node.blockQps(), node.totalQps(), node.avgRt(),
                    node.totalRequest() - node.blockRequest(), node.blockRequest(), node.totalRequest()));
        }
        for (Node n : node.getChildList()) {
            DefaultNode dn = (DefaultNode)n;
            visitTree(level + 1, dn);
        }
    }

}
