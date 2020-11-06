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

import com.google.dualhomelab.monitoringagent.resourcemonitoring.Attribute;
import com.google.dualhomelab.monitoringagent.resourcemonitoring.LabResource;
import com.google.dualhomelab.monitoringagent.resourcemonitoring.LabResourceRequest;
import com.google.dualhomelab.monitoringagent.resourcemonitoring.MonitoredEntity;

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
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.grpc.testing.StreamRecorder;

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
    public void setUp() {
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

    @Test
    public void testServerStartAndShutdown() {
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
    }

    @Test
    public void testGetMonitoredHost() {
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
                mMonitor.buildMonitoredHost());
    }

    @Test
    public void testGetMonitoredDeviceIdentifier() {
        when(mClusterOptions.getRunTargetFormat()).thenReturn(null);
        when(mClusterOptions.getDeviceTag()).thenReturn(null);
        when(mClusterOptions.getNextClusterIds()).thenReturn(Arrays.asList("foo", "bar"));
        MonitoredEntity device = mMonitor.buildMonitoredDevice(DEVICE_DESCRIPTOR);
        Assert.assertEquals("fake-serial", device.getIdentifierOrThrow("device_serial"));
    }

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

    @Test
    public void testGetStatus() {
        Assert.assertEquals(
                "Available", mMonitor.getStatus(DEVICE_DESCRIPTOR).getMetric(0).getTag());
    }

    @Test
    public void testCachedResponseValid() throws InterruptedException {
        CachedLabResource resource = new CachedLabResource(LabResource.newBuilder().build());
        Assert.assertTrue(resource.isValid(3));
        Thread.sleep(3001);
        Assert.assertFalse(resource.isValid(3));
    }

    @Test
    public void testCachedResponseLogic() throws Exception {
        when(mClusterOptions.getLabName()).thenReturn("foo-lab");
        when(mClusterOptions.getClusterId()).thenReturn("zoo");
        Assert.assertFalse(mMonitor.getCachedLabResource().isPresent());
        StreamRecorder<LabResource> responseObserver = StreamRecorder.create();
        mMonitor.getLabResource(LabResourceRequest.newBuilder().build(), responseObserver);
        Assert.assertTrue(responseObserver.awaitCompletion(1, TimeUnit.SECONDS));
        Assert.assertNull(responseObserver.getError());
        CachedLabResource cachedResource = mMonitor.getCachedLabResource().get();
        mMonitor.getLabResource(LabResourceRequest.newBuilder().build(), responseObserver);
        Assert.assertEquals(
                mMonitor.getCachedLabResource().get().mInstant, cachedResource.mInstant);
        Thread.sleep(3001);
        mMonitor.getLabResource(LabResourceRequest.newBuilder().build(), responseObserver);
        Assert.assertTrue(
                mMonitor.getCachedLabResource().get().mInstant.isAfter(cachedResource.mInstant));
    }
}
