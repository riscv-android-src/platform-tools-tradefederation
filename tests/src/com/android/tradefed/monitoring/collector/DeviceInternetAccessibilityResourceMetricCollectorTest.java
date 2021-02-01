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

package com.android.tradefed.monitoring.collector;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.mockito.Mockito.*;

import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import com.google.dualhomelab.monitoringagent.resourcemonitoring.Resource;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

/** Tests for {@link DeviceInternetAccessibilityResourceMetricCollector}. */
public class DeviceInternetAccessibilityResourceMetricCollectorTest {
    private static final String MOCK_SUCCESS_RESPONSE =
            String.join(
                    "\n",
                    "PING google.com (172.217.24.14) 56(84) bytes of data.",
                    "64 bytes from tsa01s07-in-f14.1e100.net (172.217.24.14): icmp_seq=1 ttl=57"
                            + " time=26.2 ms",
                    "--- google.com ping statistics ---",
                    "1 packets transmitted, 1 received, 0% packet loss, time 0ms",
                    "rtt min/avg/max/mdev = 26.0/26.0/26.0/0.000 ms");
    @Mock private IDeviceManager mDeviceManager;
    @Rule public MockitoRule rule = MockitoJUnit.rule();
    private final DeviceDescriptor mDescriptor =
            new DeviceDescriptor() {
                @Override
                public String getSerial() {
                    return "foo";
                }
            };
    private final DeviceInternetAccessibilityResourceMetricCollector mCollector =
            new DeviceInternetAccessibilityResourceMetricCollector();

    /** Tests no internet accessibility. */
    @Test
    public void testGetDeviceResource_not_accessible() {
        when(mDeviceManager.executeCmdOnAvailableDevice(
                        "foo",
                        DeviceInternetAccessibilityResourceMetricCollector.PING_CMD,
                        500,
                        TimeUnit.MILLISECONDS))
                .thenReturn(
                        new CommandResult() {
                            @Override
                            public CommandStatus getStatus() {
                                return CommandStatus.SUCCESS;
                            }

                            @Override
                            public String getStdout() {
                                return "ping: unknown host google.com";
                            }
                        });
        Collection<Resource> resources =
                mCollector.getDeviceResourceMetrics(mDescriptor, mDeviceManager);
        Assert.assertEquals(1, resources.size());
        Resource resource = resources.iterator().next();
        Assert.assertEquals(0.f, resource.getMetric(0).getValue(), 0.f);
    }

    /** Tests successful internet access. */
    @Test
    public void testGetDeviceResource_success() {
        when(mDeviceManager.executeCmdOnAvailableDevice(
                        "foo",
                        DeviceInternetAccessibilityResourceMetricCollector.PING_CMD,
                        500,
                        TimeUnit.MILLISECONDS))
                .thenReturn(
                        new CommandResult() {
                            @Override
                            public CommandStatus getStatus() {
                                return CommandStatus.SUCCESS;
                            }

                            @Override
                            public String getStdout() {
                                return MOCK_SUCCESS_RESPONSE;
                            }
                        });
        Collection<Resource> resources =
                mCollector.getDeviceResourceMetrics(mDescriptor, mDeviceManager);
        Assert.assertEquals(1, resources.size());
        Resource resource = resources.iterator().next();
        Assert.assertEquals(26.0, resource.getMetric(0).getValue(), 0.f);
    }
}
