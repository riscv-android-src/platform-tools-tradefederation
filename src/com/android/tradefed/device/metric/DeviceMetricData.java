/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tradefed.device.metric;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;

import com.google.common.base.Preconditions;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Object to hold all the data collected by metric collectors. TODO: Add the data holding and
 * receiving of data methods.
 */
public class DeviceMetricData implements Serializable {
    private static final long serialVersionUID = 1;
    // When collecting metrics for multiple devices, the configuration name of the device is added
    // as a namespace to differentiate the metrics.
    private static final String DEVICE_NAME_FORMAT_KEY = "{%s}:%s";

    // TODO: expend type supports to more complex type: Object, File, etc.
    private Map<String, String> mCurrentStringMetrics =
            Collections.synchronizedMap(new LinkedHashMap<String, String>());

    private final IInvocationContext mContext;

    /** Ctor */
    public DeviceMetricData(IInvocationContext context) {
        mContext = context;
    }

    /**
     * Add a single metric associated with the primary device.
     *
     * @param key The key of the metric.
     * @param value The value associated with the metric.
     */
    public void addStringMetric(String key, String value) {
        synchronized (mCurrentStringMetrics) {
            String deviceName = null;
            if (mContext.getDevices().size() > 1) {
                // If there is more than one device, default add is for first device.
                deviceName = mContext.getDeviceName(mContext.getDevices().get(0));
                mCurrentStringMetrics.put(
                        String.format(DEVICE_NAME_FORMAT_KEY, deviceName, key), value);
            } else {
                mCurrentStringMetrics.put(key, value);
            }
        }
    }

    /**
     * Add a single metric associated with a specified device.
     *
     * @param device the {@link ITestDevice} the metric is associated to.
     * @param key The key of the metric.
     * @param value The value associated with the metric.
     */
    public void addStringMetricForDevice(ITestDevice device, String key, String value) {
        synchronized (mCurrentStringMetrics) {
            if (mContext.getDevices().size() > 1) {
                String deviceName = mContext.getDeviceName(device);
                mCurrentStringMetrics.put(
                        String.format(DEVICE_NAME_FORMAT_KEY, deviceName, key), value);
            } else {
                mCurrentStringMetrics.put(key, value);
            }
        }
    }

    /**
     * Push all the data received so far to the map of metrics that will be reported. This should
     * also clean up the resources after pushing them.
     *
     * @param metrics The metrics currently available.
     */
    public void addToMetrics(Map<String, String> metrics) {
        Preconditions.checkNotNull(metrics);
        // TODO: dump all the metrics collected to the map of metrics to be reported.
        synchronized (mCurrentStringMetrics) {
            metrics.putAll(mCurrentStringMetrics);
        }
    }
}
