/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.servicecomb.metrics.core;

import static org.mockito.Mockito.when;

import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import io.servicecomb.core.metrics.InvocationFinishedEvent;
import io.servicecomb.core.metrics.InvocationStartProcessingEvent;
import io.servicecomb.core.metrics.InvocationStartedEvent;
import io.servicecomb.foundation.common.utils.EventUtils;
import io.servicecomb.metrics.core.event.DefaultEventListenerManager;
import io.servicecomb.metrics.core.extra.DefaultSystemResource;
import io.servicecomb.metrics.core.metric.RegistryMetric;
import io.servicecomb.metrics.core.monitor.RegistryMonitor;
import io.servicecomb.metrics.core.publish.DefaultDataSource;
import io.servicecomb.swagger.invocation.InvocationType;

public class TestEventAndRunner {

  @Test
  public void test() throws InterruptedException {
    OperatingSystemMXBean systemMXBean = Mockito.mock(OperatingSystemMXBean.class);
    when(systemMXBean.getSystemLoadAverage()).thenReturn(1.0);
    ThreadMXBean threadMXBean = Mockito.mock(ThreadMXBean.class);
    when(threadMXBean.getThreadCount()).thenReturn(2);
    MemoryMXBean memoryMXBean = Mockito.mock(MemoryMXBean.class);
    MemoryUsage heap = Mockito.mock(MemoryUsage.class);
    when(memoryMXBean.getHeapMemoryUsage()).thenReturn(heap);
    when(heap.getCommitted()).thenReturn(100L);
    when(heap.getInit()).thenReturn(200L);
    when(heap.getMax()).thenReturn(300L);
    when(heap.getUsed()).thenReturn(400L);
    MemoryUsage nonHeap = Mockito.mock(MemoryUsage.class);
    when(memoryMXBean.getNonHeapMemoryUsage()).thenReturn(nonHeap);
    when(nonHeap.getCommitted()).thenReturn(500L);
    when(nonHeap.getInit()).thenReturn(600L);
    when(nonHeap.getMax()).thenReturn(700L);
    when(nonHeap.getUsed()).thenReturn(800L);

    RegistryMonitor monitor = new RegistryMonitor();
    DefaultSystemResource systemResource = new DefaultSystemResource(systemMXBean, threadMXBean, memoryMXBean);
    DefaultDataSource dataSource = new DefaultDataSource(systemResource, monitor, "2000");

    DefaultEventListenerManager manager = new DefaultEventListenerManager(monitor);

    EventUtils.triggerEvent(new InvocationStartedEvent("fun1", System.nanoTime()));
    EventUtils.triggerEvent(
        new InvocationStartProcessingEvent("fun1", InvocationType.PRODUCER, System.nanoTime(),
            TimeUnit.MILLISECONDS.toNanos(100)));
    EventUtils
        .triggerEvent(new InvocationFinishedEvent("fun1", InvocationType.PRODUCER, System.nanoTime(),
            TimeUnit.MILLISECONDS.toNanos(200), TimeUnit.MILLISECONDS.toNanos(300)));

    EventUtils.triggerEvent(new InvocationStartedEvent("fun1", System.nanoTime()));
    EventUtils.triggerEvent(
        new InvocationStartProcessingEvent("fun1", InvocationType.PRODUCER, System.nanoTime(),
            TimeUnit.MILLISECONDS.toNanos(300)));
    EventUtils
        .triggerEvent(new InvocationFinishedEvent("fun1", InvocationType.PRODUCER, System.nanoTime(),
            TimeUnit.MILLISECONDS.toNanos(400), TimeUnit.MILLISECONDS.toNanos(700)));

    EventUtils.triggerEvent(new InvocationStartedEvent("fun12", System.nanoTime()));
    EventUtils.triggerEvent(
        new InvocationStartProcessingEvent("fun12", InvocationType.PRODUCER, System.nanoTime(),
            TimeUnit.MILLISECONDS.toNanos(500)));
    EventUtils
        .triggerEvent(new InvocationFinishedEvent("fun12", InvocationType.PRODUCER, System.nanoTime(),
            TimeUnit.MILLISECONDS.toNanos(600), TimeUnit.MILLISECONDS.toNanos(1100)));

    EventUtils.triggerEvent(new InvocationStartedEvent("fun11", System.nanoTime()));

    Thread.sleep(2000);

    RegistryMetric model = dataSource.getRegistryMetric(0);

    Assert.assertEquals(model.getInvocationMetrics().get("fun1").getWaitInQueue(), 0);
    Assert.assertEquals(model.getInvocationMetrics().get("fun11").getWaitInQueue(), 1);
    Assert.assertEquals(model.getInstanceMetric().getWaitInQueue(), 1);

    Assert.assertEquals(model.getInvocationMetrics().get("fun1").getLifeTimeInQueue().getMin(),
        TimeUnit.MILLISECONDS.toNanos(100),
        0);
    Assert.assertEquals(model.getInvocationMetrics().get("fun1").getLifeTimeInQueue().getMax(),
        TimeUnit.MILLISECONDS.toNanos(300),
        0);
    Assert.assertEquals(
        model.getInvocationMetrics().get("fun1").getLifeTimeInQueue().getAverage(), TimeUnit.MILLISECONDS.toNanos(200),
        0);
    Assert.assertEquals(model.getInstanceMetric().getLifeTimeInQueue().getMin(), TimeUnit.MILLISECONDS.toNanos(100), 0);
    Assert.assertEquals(model.getInstanceMetric().getLifeTimeInQueue().getMax(), TimeUnit.MILLISECONDS.toNanos(500), 0);
    Assert.assertEquals(model.getInstanceMetric().getLifeTimeInQueue().getAverage(), TimeUnit.MILLISECONDS.toNanos(300),
        0);

    Assert
        .assertEquals(model.getInvocationMetrics().get("fun1").getExecutionTime().getMin(),
            TimeUnit.MILLISECONDS.toNanos(200), 0);
    Assert
        .assertEquals(model.getInvocationMetrics().get("fun1").getExecutionTime().getMax(),
            TimeUnit.MILLISECONDS.toNanos(400), 0);
    Assert
        .assertEquals(model.getInvocationMetrics().get("fun1").getExecutionTime().getAverage(),
            TimeUnit.MILLISECONDS.toNanos(300),
            0);
    Assert.assertEquals(model.getInstanceMetric().getExecutionTime().getMin(), TimeUnit.MILLISECONDS.toNanos(200), 0);
    Assert.assertEquals(model.getInstanceMetric().getExecutionTime().getMax(), TimeUnit.MILLISECONDS.toNanos(600), 0);
    Assert
        .assertEquals(model.getInstanceMetric().getExecutionTime().getAverage(), TimeUnit.MILLISECONDS.toNanos(400), 0);

    Assert
        .assertEquals(model.getInvocationMetrics().get("fun1").getProducerLatency().getMin(),
            TimeUnit.MILLISECONDS.toNanos(300), 0);
    Assert
        .assertEquals(model.getInvocationMetrics().get("fun1").getProducerLatency().getMax(),
            TimeUnit.MILLISECONDS.toNanos(700), 0);
    Assert
        .assertEquals(model.getInvocationMetrics().get("fun1").getProducerLatency().getAverage(),
            TimeUnit.MILLISECONDS.toNanos(500),
            0);
    Assert.assertEquals(model.getInstanceMetric().getProducerLatency().getMin(), TimeUnit.MILLISECONDS.toNanos(300), 0);
    Assert
        .assertEquals(model.getInstanceMetric().getProducerLatency().getMax(), TimeUnit.MILLISECONDS.toNanos(1100), 0);
    Assert
        .assertEquals(model.getInstanceMetric().getProducerLatency().getAverage(), TimeUnit.MILLISECONDS.toNanos(700),
            0);

    Assert
        .assertEquals(model.getInvocationMetrics().get("fun1").getConsumerLatency().getMin(),
            TimeUnit.MILLISECONDS.toNanos(0), 0);
    Assert
        .assertEquals(model.getInvocationMetrics().get("fun1").getConsumerLatency().getMax(),
            TimeUnit.MILLISECONDS.toNanos(0), 0);
    Assert
        .assertEquals(model.getInvocationMetrics().get("fun1").getConsumerLatency().getAverage(),
            TimeUnit.MILLISECONDS.toNanos(0),
            0);
    Assert.assertEquals(model.getInstanceMetric().getConsumerLatency().getMin(), TimeUnit.MILLISECONDS.toNanos(0), 0);
    Assert.assertEquals(model.getInstanceMetric().getConsumerLatency().getMax(), TimeUnit.MILLISECONDS.toNanos(0), 0);
    Assert
        .assertEquals(model.getInstanceMetric().getConsumerLatency().getAverage(), TimeUnit.MILLISECONDS.toNanos(0), 0);

    Assert.assertEquals(model.getInstanceMetric().getSystemMetric().getCpuLoad(), 1.0, 0);
    Assert.assertEquals(model.getInstanceMetric().getSystemMetric().getCpuRunningThreads(), 2, 0);
    Assert.assertEquals(model.getInstanceMetric().getSystemMetric().getHeapCommit(), 100, 0);
    Assert.assertEquals(model.getInstanceMetric().getSystemMetric().getHeapInit(), 200, 0);
    Assert.assertEquals(model.getInstanceMetric().getSystemMetric().getHeapMax(), 300, 0);
    Assert.assertEquals(model.getInstanceMetric().getSystemMetric().getHeapUsed(), 400, 0);
    Assert.assertEquals(model.getInstanceMetric().getSystemMetric().getNonHeapCommit(), 500, 0);
    Assert.assertEquals(model.getInstanceMetric().getSystemMetric().getNonHeapInit(), 600, 0);
    Assert.assertEquals(model.getInstanceMetric().getSystemMetric().getNonHeapMax(), 700, 0);
    Assert.assertEquals(model.getInstanceMetric().getSystemMetric().getNonHeapUsed(), 800, 0);
  }
}