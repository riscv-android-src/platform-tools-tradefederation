/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tradefed.postprocessor;

import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ILogSaverListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.util.IDisableable;

import java.util.HashMap;
import java.util.Map;

/**
 * Post processors is a Trade Federation object meant to allow the processing of metrics and logs
 * AFTER the tests and BEFORE result reporting. This allows to post-process some data and have all
 * result_reporter objects receive it, rather than doing the post-processing inside only one
 * result_reporter and having issue to pass the new data around.
 */
public interface IPostProcessor extends ITestInvocationListener, ILogSaverListener, IDisableable {

    /**
     * Initialization step of the post processor. Ensured to be called before any of the tests
     * callbacks.
     */
    public ITestInvocationListener init(ITestInvocationListener listener);

    /**
     * Implement this method in order to generate a set of new metrics from the existing one. Only
     * the newly generated metrics should be returned, and with unique key name (no collision with
     * existing keys are allowed).
     *
     * @param rawMetrics The set of raw metrics available for the run.
     * @return The set of newly generated metrics.
     */
    public Map<String, Metric.Builder> processRunMetrics(HashMap<String, Metric> rawMetrics);
}
