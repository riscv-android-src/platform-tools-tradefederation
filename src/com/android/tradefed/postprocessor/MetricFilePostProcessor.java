/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.annotations.VisibleForTesting;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric.Builder;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.util.MetricUtility;
import com.android.tradefed.util.proto.TfMetricProtoUtil;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Used for uploading the metrics log file collected during the test and run level.
 *
 * Use "aggregate-similar-tests" option to aggregate similar tests metrics at the test run level
 * and write it to a log file. Tests differ only by the iteration number or with the same name
 * are considered similar tests.
 *
 * This will have access to only raw metrics.
 */
@OptionClass(alias = "metric-file-post-processor")
public class MetricFilePostProcessor extends BasePostProcessor {

    @Option(name = "enable-per-test-log", description = "Set this flag to false to disable"
            + " writing the per test metrics to a file.")
    private boolean mIsPerTestLogEnabled= true;

    @Option(name = "enable-run-log", description = "Set this flag to false to disable"
            + " writing the run metrics to a file.")
    private boolean mIsRunLogEnabled= true;

    @Option(name = "aggregate-similar-tests", description = "To aggregate the metrics from test"
            + " cases which differ only by iteration number or having the same test name."
            + " Used only in context with the microbenchmark test runner. Set this flag to false"
            + " to disable aggregating the metrics.")
    private boolean mAggregateSimilarTests= false;

    @Option(name = "test-iteration-separator", description = "Separator used in between the test"
            + " class name and the iteration number.")
    private String mTestIterationSeparator = "$";

    @Option(name = "report-percentiles", description = "Additional percentiles of each metric to"
            + " report in integers in the 0 - 100 range. Can be repeated.")
    private Set<Integer> mPercentiles = new HashSet<>();

    private MetricUtility mMetricUtil = new MetricUtility();

    public MetricFilePostProcessor() {
    }

    @VisibleForTesting
    public MetricFilePostProcessor(MetricUtility metricUtil) {
        mMetricUtil = metricUtil;
    }

    @Override
    public Map<String, Metric.Builder> processTestMetricsAndLogs(
            TestDescription testDescription,
            HashMap<String, Metric> testMetrics,
            Map<String, LogFile> testLogs) {
        initializeMetricUtilArgs();
        Map<String, String> compatibleTestMetrics = TfMetricProtoUtil
                .compatibleConvert(testMetrics);

        // Store the test metric and use it for aggregation later at the end of
        // test run.
        if (mAggregateSimilarTests) {
            mMetricUtil.storeTestMetrics(testDescription, testMetrics);
        }

        // Write test metric to a file and log it.
        if (mIsPerTestLogEnabled) {
            String testId = testDescription.toString();
            File metricFile = mMetricUtil.writeResultsToFile(testId, testId,
                    compatibleTestMetrics,
                    null);
            if (metricFile != null) {
                try (InputStreamSource source = new FileInputStreamSource(metricFile, true)) {
                    testLog(metricFile.getName(), LogDataType.TEXT, source);
                }
            }
        }

        return new HashMap<String, Builder>();
    }

    @Override
    public Map<String, Builder> processRunMetricsAndLogs(
            HashMap<String, Metric> rawMetrics, Map<String, LogFile> runLogs) {
        initializeMetricUtilArgs();
        // Aggregate and log metrics collected at the run level.
        if (mIsRunLogEnabled) {
            Map<String, Metric> aggregatedRunMetrics = mMetricUtil.aggregateMetrics(rawMetrics);
            // Write metric to a result file and log it.
            Map<String, String> compatibleRunMetrics = TfMetricProtoUtil
                    .compatibleConvert(aggregatedRunMetrics);
            File runMetricFile = mMetricUtil.writeResultsToFile(getRunName(), getRunName(),
                    compatibleRunMetrics,
                    null);
            if (runMetricFile != null) {
                try (InputStreamSource source = new FileInputStreamSource(runMetricFile,
                        true)) {
                    testLog(runMetricFile.getName(), LogDataType.TEXT, source);
                }
            }

        }

        // Aggregate similar tests metric at the run level.
        if (mAggregateSimilarTests) {
            File aggregateTestResultsFile = mMetricUtil
                    .aggregateStoredTestMetricsAndWriteToFile(getRunName());
            if (aggregateTestResultsFile != null) {
                try (InputStreamSource source = new FileInputStreamSource(aggregateTestResultsFile,
                        true)) {
                    testLog(aggregateTestResultsFile.getName(), LogDataType.TEXT, source);
                }
            }
        }

        return new HashMap<String, Builder>();
    }

    private void initializeMetricUtilArgs() {
        mMetricUtil.setPercentiles(mPercentiles);
        mMetricUtil.setIterationSeparator(mTestIterationSeparator);
    }
}
