/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tradefed.monitoring;

import com.android.tradefed.cluster.ClusterHostUtil;
import com.android.tradefed.cluster.ClusterOptions;
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.device.DeviceAllocationState;
import com.android.tradefed.device.IDeviceMonitor;
import com.android.tradefed.monitoring.collector.IResourceMetricCollector;
import com.android.tradefed.util.RunUtil;

import com.google.dualhomelab.monitoringagent.resourcemonitoring.Attribute;
import com.google.dualhomelab.monitoringagent.resourcemonitoring.MonitoredEntity;
import com.google.dualhomelab.monitoringagent.resourcemonitoring.Resource;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import static org.mockito.Mockito.*;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;


@RunWith(JUnit4.class)
public class LabResourceDeviceMonitorTest {
    @Mock private ClusterOptions mClusterOptions;

    @Rule public MockitoRule rule = MockitoJUnit.rule();

    private static final DeviceDescriptor DEVICE_DESCRIPTOR =
            new DeviceDescriptor(
                    "fake-serial",
                    false,
                    DeviceAllocationState.Available,
                    "product",
                    "productVariant",
                    "sdkVersion",
                    "buildId",
                    "batteryLevel");

    private LabResourceDeviceMonitor mMonitor;

    @Before
    public void setUp() throws Exception {
        mMonitor = new LabResourceDeviceMonitor(3, mClusterOptions);
        mMonitor.setDeviceLister(
                new IDeviceMonitor.DeviceLister() {
                    @Override
                    public List<DeviceDescriptor> listDevices() {
                        return List.of(DEVICE_DESCRIPTOR);
                    }

                    @Override
                    public DeviceDescriptor getDeviceDescriptor(String serial) {
                        return null;
                    }
                });
    }

    /** Tests gRPC server and metricize thread bring up and shutdown. */
    @Test
    public void testServerStartAndShutdown() throws InterruptedException {
        when(mClusterOptions.getLabName()).thenReturn("foo-lab");
        when(mClusterOptions.getClusterId()).thenReturn("zoo");
        Assert.assertFalse(
                "server should be empty before monitor run", mMonitor.getServer().isPresent());
        mMonitor.run();
        Assert.assertTrue(
                "server should present after monitor run", mMonitor.getServer().isPresent());
        Assert.assertEquals(
                LabResourceDeviceMonitor.DEFAULT_PORT, mMonitor.getServer().get().getPort());
        mMonitor.stop();
        Assert.assertTrue(
                "server should be shutdown after monitor stop",
                mMonitor.getServer().get().isShutdown());
        mMonitor.getServer().get().awaitTermination();
        Thread.sleep(2000); // Add extra time to wait for gRPC server to terminate threads.
    }

    /** Tests building host entity. */
    @Test
    public void testBuildMonitoredHost() {
        when(mClusterOptions.getLabName()).thenReturn("foo-lab");
        when(mClusterOptions.getClusterId()).thenReturn("zoo");
        Assert.assertEquals(
                MonitoredEntity.newBuilder()
                        .putIdentifier(
                                LabResourceDeviceMonitor.HOST_NAME_KEY,
                                ClusterHostUtil.getHostName())
                        .putIdentifier(LabResourceDeviceMonitor.LAB_NAME_KEY, "foo-lab")
                        .putIdentifier(
                                LabResourceDeviceMonitor.TEST_HARNESS_KEY,
                                LabResourceDeviceMonitor.TEST_HARNESS)
                        .addAttribute(
                                Attribute.newBuilder()
                                        .setName(LabResourceDeviceMonitor.HOST_GROUP_KEY)
                                        .setValue("zoo"))
                        .build(),
                mMonitor.buildMonitoredHost(List.of()));
    }

    /** Tests building device entity. */
    @Test
    public void testBuildMonitoredDeviceIdentifier() {
        when(mClusterOptions.getRunTargetFormat()).thenReturn(null);
        when(mClusterOptions.getDeviceTag()).thenReturn(null);
        when(mClusterOptions.getNextClusterIds()).thenReturn(Arrays.asList("foo", "bar"));
        MonitoredEntity device = mMonitor.buildMonitoredDevice(DEVICE_DESCRIPTOR, List.of());
        Assert.assertEquals("fake-serial", device.getIdentifierOrThrow("device_serial"));
    }

    /** Tests composing device attributes. */
    @Test
    public void testGetDeviceAttributes() {
        when(mClusterOptions.getRunTargetFormat()).thenReturn(null);
        when(mClusterOptions.getDeviceTag()).thenReturn(null);
        when(mClusterOptions.getNextClusterIds()).thenReturn(Arrays.asList("foo", "bar"));
        Assert.assertEquals(
                Arrays.asList(
                        Attribute.newBuilder()
                                .setName("run_target")
                                .setValue("product:productVariant")
                                .build(),
                        Attribute.newBuilder().setName("pool").setValue("foo").build(),
                        Attribute.newBuilder().setName("pool").setValue("bar").build()),
                mMonitor.getDeviceAttributes(DEVICE_DESCRIPTOR));
    }

    /** Tests composing device status. */
    @Test
    public void testGetStatus() {
        Assert.assertEquals(
                "Available", mMonitor.getStatus(DEVICE_DESCRIPTOR).getMetric(0).getTag());
    }

    class MockCollector implements IResourceMetricCollector {

        boolean isFinished = false;

        @Override
        public Collection<Resource> getHostResourceMetrics() {
            while (!Thread.currentThread().isInterrupted()) {}

            isFinished = true;
            return List.of(Resource.newBuilder().build());
        }
    }

    /** Tests collector operation timeout and trigger next collector operation. */
    @Test
    public void testCollectorTimeout() throws InterruptedException {
        when(mClusterOptions.getLabName()).thenReturn("foo-lab");
        when(mClusterOptions.getClusterId()).thenReturn("zoo");
        MockCollector collector1 = new MockCollector();
        MockCollector collector2 = new MockCollector();
        mMonitor.run();
        mMonitor.buildMonitoredHost(List.of(collector1, collector2));
        mMonitor.getSharedCollectorExecutor().shutdown();
        mMonitor.getSharedCollectorExecutor().awaitTermination(3, TimeUnit.SECONDS);
        mMonitor.stop();
        Assert.assertTrue(collector1.isFinished);
        Assert.assertTrue(collector2.isFinished);
        RunUtil.getDefault()
                .sleep(2000); // Add extra time to wait for gRPC server to terminate threads.
    }

}
