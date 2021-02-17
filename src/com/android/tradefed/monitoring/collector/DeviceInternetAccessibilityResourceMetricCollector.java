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

import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.device.IDeviceManager;

import com.google.dualhomelab.monitoringagent.resourcemonitoring.Metric;
import com.google.dualhomelab.monitoringagent.resourcemonitoring.Resource;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The collector pings google.com to check if the device has internet accessibility or not. */
public class DeviceInternetAccessibilityResourceMetricCollector
        implements IResourceMetricCollector {
    public static final String INTERNET_ACCESSIBILITY_METRIC_NAME = "internet_access";
    /*
    The example response:
    PING google.com (172.217.27.142) 56(84) bytes of data.
    64 bytes from tsa03s02-in-f14.1e100.net (172.217.27.142): icmp_seq=1 ttl=116 time=4.63 ms

    --- google.com ping statistics ---
    1 packets transmitted, 1 received, 0% packet loss, time 0ms
    rtt min/avg/max/mdev = 4.638/4.638/4.638/0.000 ms
    */
    public static final String PING_CMD = "ping -c 1 google.com";
    public static final Pattern SUCCESS_PATTERN =
            Pattern.compile("rtt min\\/avg\\/max\\/mdev = " + "[0-9\\.]+\\/(?<avgping>[0-9.]+)\\/");
    public static final String AVG_PING = "avgping";
    public static final float FAILED_VAL = 0.f;
    private static final long CMD_TIMEOUT_MS = 500;

    /** Issues ping command to collect internet accessibility metrics. */
    @Override
    public Collection<Resource> getDeviceResourceMetrics(
            DeviceDescriptor descriptor, IDeviceManager deviceManager) {
        final Optional<String> response =
                ResourceMetricUtil.GetCommandResponse(
                        deviceManager, descriptor.getSerial(), PING_CMD, CMD_TIMEOUT_MS);
        if (!response.isPresent()) {
            return List.of();
        }
        final Resource.Builder builder =
                Resource.newBuilder()
                        .setResourceName(INTERNET_ACCESSIBILITY_METRIC_NAME)
                        .setTimestamp(ResourceMetricUtil.GetCurrentTimestamp());
        final Matcher matcher = SUCCESS_PATTERN.matcher(response.get());
        final Metric.Builder metricBuilder = Metric.newBuilder().setTag(AVG_PING);
        if (!matcher.find()) {
            metricBuilder.setValue(FAILED_VAL);
        } else {
            metricBuilder.setValue(ResourceMetricUtil.RoundedMetricValue(matcher.group(AVG_PING)));
        }
        builder.addMetric(metricBuilder);
        return List.of(builder.build());
    }
}
