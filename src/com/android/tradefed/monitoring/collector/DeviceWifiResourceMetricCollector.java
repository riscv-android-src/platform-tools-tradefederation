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

/** The collector collects current connected wifi signal strength and link speed metrics. */
public class DeviceWifiResourceMetricCollector implements IResourceMetricCollector {
    public static final String WIFI_METRIC_NAME = "wifi";
    /* No WiFi connection response:
    RSSI=-9999
    LINKSPEED=0
    NOISE=-119
    FREQUENCY=2412
    WiFi connection response:
    RSSI=-76
    LINKSPEED=162
    NOISE=-103
    FREQUENCY=5500
    AVG_RSSI=-77
    */
    public static final String WIFI_METRIC_CMD = "wpa_cli -i wlan0 signal_poll";
    public static final Pattern WIFI_METRIC_PATTERN =
            Pattern.compile(
                    "RSSI=(?<rssi>[\\-0-9]+)\\nLINKSPEED=(?<speed>[0-9]+)\\nNOISE="
                            + "(?<noise>[\\-0-9]+)\\n");
    public static final String RSSI = "rssi";
    public static final String SPEED = "speed";
    public static final String NOISE = "noise";
    private static final long CMD_TIMEOUT_MS = 500;

    /** Issues adb shell command and parses the WiFi metrics. */
    @Override
    public Collection<Resource> getDeviceResourceMetrics(
            DeviceDescriptor descriptor, IDeviceManager deviceManager) {
        final Optional<String> response =
                ResourceMetricUtil.GetCommandResponse(
                        deviceManager, descriptor.getSerial(), WIFI_METRIC_CMD, CMD_TIMEOUT_MS);
        if (!response.isPresent()) {
            return List.of();
        }
        final Matcher matcher = WIFI_METRIC_PATTERN.matcher(response.get());
        if (!matcher.find()) {
            return List.of();
        }
        Resource.Builder builder =
                Resource.newBuilder()
                        .setResourceName(WIFI_METRIC_NAME)
                        .setTimestamp(ResourceMetricUtil.GetCurrentTimestamp());
        float speed = ResourceMetricUtil.RoundedMetricValue(matcher.group(SPEED));
        if (speed == 0.f) {
            // If the speed is 0, the rest metrics are meaningless.
            return List.of(
                    builder.addMetric(Metric.newBuilder().setTag(SPEED).setValue(speed)).build());
        }
        builder.addMetric(Metric.newBuilder().setTag(SPEED).setValue(speed))
                .addMetric(
                        Metric.newBuilder()
                                .setTag(RSSI)
                                .setValue(
                                        ResourceMetricUtil.RoundedMetricValue(matcher.group(RSSI))))
                .addMetric(
                        Metric.newBuilder()
                                .setTag(NOISE)
                                .setValue(
                                        ResourceMetricUtil.RoundedMetricValue(
                                                matcher.group(NOISE))));
        return List.of(builder.build());
    }
}
