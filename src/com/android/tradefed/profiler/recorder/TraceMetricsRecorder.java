/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tradefed.profiler.recorder;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.StreamUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * An {@link IMetricsRecorder} that records metrics taken from the /d/tracing directory.
 *
 * Metrics to be recorded need to be provided as TraceMetrics. The default descriptor
 * has the format prefix:funcname:param[=expectedval]:metrictype.
 */
public class TraceMetricsRecorder implements IMetricsRecorder {

    private static final String TRACE_DIR = "/d/tracing";
    private static final String EVENT_DIR = TRACE_DIR + "/events/";

    private Map<String, TraceMetric> mTraceMetrics;
    private Map<TraceMetric, BiFunction<Double, Double, Double>> mMergeFunctions;
    private TraceParser mParser;

    // tracing_on setting before the test
    private String mTracingBefore;
    // single event trace settings before the test. Format: (path to "enable" file, setting before)
    private Map<String, String> mSingleEventTracingBefore = new HashMap<>();

    @Override
    public void setUp(ITestDevice device, Collection<String> descriptors)
            throws DeviceNotAvailableException {
        mMergeFunctions = new HashMap<>();
        mTraceMetrics = new HashMap<>();
        mParser = new TraceParser();
        for (String descriptor : descriptors) {
            TraceMetric metric = TraceMetric.parse(descriptor);
            enableSingleEventTracing(device, metric.getPrefix() + "/" + metric.getFuncName());
            mTraceMetrics.put(metric.getFuncName(), metric);
            mMergeFunctions.put(
                    metric, new NumericAggregateFunction(metric.getMetricType()).getFunction());
        }
    }

    @Override
    public void startMetrics(ITestDevice device) throws DeviceNotAvailableException {
        enableTracing(device);
    }

    @Override
    public Map<String, Double> stopMetrics(ITestDevice device) throws DeviceNotAvailableException {
        undoEnableTracing(device);
        undoAllEnableSingleEventTracing(device);
        File fullTrace = pullFullTrace(device);
        cleanUpTrace(device);
        return parseTraceFile(fullTrace);
    }

    public Map<String, Double> parseTraceFile(File fullTrace) {
        Map<String, Double> metrics = new HashMap<>();
        BufferedReader trace = null;
        try {
            trace = getReaderFromFile(fullTrace);
            String line;
            Double lastTimestamp = null;
            while ((line = trace.readLine()) != null) {
                if (line.startsWith("#")) {
                    continue;
                }
                line = line.trim();
                CLog.v("Got trace line %s", line);
                TraceLine descriptor = mParser.parseTraceLine(line);
                TraceMetric matchedMetric = mTraceMetrics.get(descriptor.getFunctionName());
                // There's no template for handling this metric, so ignore it.
                if (matchedMetric == null) {
                    continue;
                }

                //TODO(ghliu): do not handle specific MetricType here
                if (matchedMetric.getMetricType() == MetricType.AVGTIME) {
                    lastTimestamp = descriptor.getTimestamp();
                    continue;
                } else if (lastTimestamp != null) {
                    double timeDiff = descriptor.getTimestamp() - lastTimestamp;
                    lastTimestamp = null;
                    metrics.merge(
                            matchedMetric.toString(), timeDiff, mMergeFunctions.get(matchedMetric));
                } else {
                    Long baseParamValue =
                            descriptor.getFunctionParams().get(matchedMetric.getParam());
                    if (baseParamValue == null) {
                        continue;
                    }
                    metrics.merge(
                            matchedMetric.toString(), Double.valueOf(baseParamValue),
                            mMergeFunctions.get(matchedMetric));
                }
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            StreamUtil.close(trace);
        }
        return metrics;
    }

    @Override
    public BiFunction<Double, Double, Double> getMergeFunction(String key) {
        CLog.i("Looking up merge function for metric %s", key);
        return mMergeFunctions.get(TraceMetric.parse(key));
    }

    @Override
    public String getName() {
        return "TraceMetricsRecorder";
    }

    /**
     * Helper function to write tracing setting ("0" or "1") to given path on device with "echo".
     */
    void setTracingSetting(ITestDevice device, String enabled, String path)
            throws DeviceNotAvailableException {
        device.executeShellCommand(String.format("echo %s > %s", enabled, path));
    }

    /**
     * Helper function to read tracing setting ("0" or "1") from given path on device with "cat".
     */
    String getTracingSetting(ITestDevice device, String path) throws DeviceNotAvailableException {
        String s = device.executeShellCommand("cat " + path);
        if (s.length() > 0) {
            // remove trailing blank line
            return s.substring(0, 1);
        } else {
            return "0";
        }
    }

    /** Enable tracing on device. */
    void enableTracing(ITestDevice device) throws DeviceNotAvailableException {
        mTracingBefore = getTracingSetting(device, TRACE_DIR + "/tracing_on");
        setTracingSetting(device, "1", TRACE_DIR + "/tracing_on");
    }

    /** Restore pre-test tracing setting. */
    void undoEnableTracing(ITestDevice device) throws DeviceNotAvailableException {
        setTracingSetting(device, mTracingBefore, TRACE_DIR + "/tracing_on");
    }

    File pullFullTrace(ITestDevice device) throws DeviceNotAvailableException {
        File fullTrace = device.pullFile(TRACE_DIR + "/trace");
        if (fullTrace == null) {
            throw new RuntimeException("Failed to pull trace file");
        }
        return fullTrace;
    }

    void cleanUpTrace(ITestDevice device) throws DeviceNotAvailableException {
        device.executeShellCommand("echo > " + TRACE_DIR + "/trace");
    }

    /** Enable single event tracing. */
    void enableSingleEventTracing(ITestDevice device, String location)
            throws DeviceNotAvailableException {
        String fullLocation = EVENT_DIR + location + "/enable";
        String enabledBefore = getTracingSetting(device, fullLocation);
        mSingleEventTracingBefore.put(fullLocation, enabledBefore);
        CLog.d("Starting event located at %s", fullLocation);
        setTracingSetting(device, "1", fullLocation);
    }

    /** Restore pre-test single event tracing settings. */
    void undoAllEnableSingleEventTracing(ITestDevice device) throws DeviceNotAvailableException {
        for (Map.Entry<String, String> entry : mSingleEventTracingBefore.entrySet()) {
            String fullLocation = entry.getKey();
            String enabledBefore = entry.getValue();
            setTracingSetting(device, enabledBefore, fullLocation);
        }
    }

    protected BufferedReader getReaderFromFile(File trace) throws FileNotFoundException {
        return new BufferedReader(new FileReader(trace));
    }
}
