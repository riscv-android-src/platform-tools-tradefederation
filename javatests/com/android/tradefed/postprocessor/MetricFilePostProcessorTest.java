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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.util.MetricUtility;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** Unit tests for {@link MetricFilePostProcessor}. */
@RunWith(JUnit4.class)
public class MetricFilePostProcessorTest {

    private MetricFilePostProcessor mMetricFilePostProcessor;
    private TestableMetricUtil mTestableMetricUtil;

    private static final TestDescription TEST_1 = new TestDescription("pkg", "test1");

    class TestableMetricUtil extends MetricUtility {
        boolean isMetricStored = false;
        boolean isMetricWrittenToFile = false;
        boolean isRunMetricAggregated = false;
        boolean isTestMetricAggregated = false;

        @Override
        public void storeTestMetrics(TestDescription testDescription,
                Map<String, Metric> testMetrics) {
            isMetricStored = true;
        }

        @Override
        public File writeResultsToFile(String testFileSuffix, String testHeaderName,
                Map<String, String> metrics, File resultsFile) {
            isMetricWrittenToFile = true;
            return null;
        }

        @Override
        public Map<String, Metric> aggregateMetrics(Map<String, Metric> rawMetrics) {
            isRunMetricAggregated = true;
            return rawMetrics;
        }

        @Override
        public File aggregateStoredTestMetricsAndWriteToFile(String runName) {
            isTestMetricAggregated = true;
            return null;
        }
    }


    @Before
    public void setUp() {
        mTestableMetricUtil = new TestableMetricUtil();
        mMetricFilePostProcessor = new MetricFilePostProcessor(mTestableMetricUtil);
    }

    @Test
    public void processTestWithMetrics() {
        HashMap<String, Metric> firstTestMetric = new HashMap<String, Metric>();
        Metric.Builder metricBuilder1 = Metric.newBuilder();
        metricBuilder1.getMeasurementsBuilder().setSingleString("2.9");
        Metric currentTestMetric = metricBuilder1.build();
        firstTestMetric.put("first_test_metric", currentTestMetric);
        mMetricFilePostProcessor.processTestMetricsAndLogs(TEST_1, firstTestMetric, null);

        assertTrue(mTestableMetricUtil.isMetricWrittenToFile);

        // Do not store the metrics since aggregation is not enabled.
        assertFalse(mTestableMetricUtil.isMetricStored);
    }

    @Test
    public void processTestEmptyMetrics() {
        HashMap<String, Metric> firstTestMetric = new HashMap<String, Metric>();
        mMetricFilePostProcessor.processTestMetricsAndLogs(TEST_1, firstTestMetric, null);

        // Create metric file with empty metrics for the test.
        assertTrue(mTestableMetricUtil.isMetricWrittenToFile);
    }

    @Test
    public void processTestMetricWithAggregate() throws ConfigurationException {
        OptionSetter setter = new OptionSetter(mMetricFilePostProcessor);
        setter.setOptionValue("aggregate-similar-tests", "true");

        HashMap<String, Metric> firstTestMetric = new HashMap<String, Metric>();
        Metric.Builder metricBuilder1 = Metric.newBuilder();
        metricBuilder1.getMeasurementsBuilder().setSingleString("2.9");
        Metric currentTestMetric = metricBuilder1.build();
        firstTestMetric.put("first_test_metric", currentTestMetric);
        mMetricFilePostProcessor.processTestMetricsAndLogs(TEST_1, firstTestMetric, null);

        assertTrue(mTestableMetricUtil.isMetricWrittenToFile);

        // Store the metrics to aggregate later at the end of test run.
        assertTrue(mTestableMetricUtil.isMetricStored);
    }

    @Test
    public void processTestMetricWithoutLogging() throws ConfigurationException {
        OptionSetter setter = new OptionSetter(mMetricFilePostProcessor);
        setter.setOptionValue("enable-per-test-log", "false");

        HashMap<String, Metric> firstTestMetric = new HashMap<String, Metric>();
        Metric.Builder metricBuilder1 = Metric.newBuilder();
        metricBuilder1.getMeasurementsBuilder().setSingleString("2.9");
        Metric currentTestMetric = metricBuilder1.build();
        firstTestMetric.put("first_test_metric", currentTestMetric);
        mMetricFilePostProcessor.processTestMetricsAndLogs(TEST_1, firstTestMetric, null);

        assertFalse(mTestableMetricUtil.isMetricWrittenToFile);
    }

    @Test
    public void processRunMetricWithLogging() {
        HashMap<String, Metric> runMetric = new HashMap<String, Metric>();
        Metric.Builder metricBuilder1 = Metric.newBuilder();
        metricBuilder1.getMeasurementsBuilder().setSingleString("2.9");
        Metric currentRunMetric = metricBuilder1.build();
        runMetric.put("run_metric", currentRunMetric);

        mMetricFilePostProcessor.processRunMetricsAndLogs(runMetric, null);

        assertTrue(mTestableMetricUtil.isRunMetricAggregated);
        assertTrue(mTestableMetricUtil.isMetricWrittenToFile);
        // By default do not attempt to aggregate the similar tests.
        assertFalse(mTestableMetricUtil.isTestMetricAggregated);

    }

    @Test
    public void processRunMetricWithSimilarTestAggregation() throws ConfigurationException {
        OptionSetter setter = new OptionSetter(mMetricFilePostProcessor);
        // Enable similar tests aggregation at the test run level.
        setter.setOptionValue("aggregate-similar-tests", "true");
        // Disabling the logging for run metrics.
        setter.setOptionValue("enable-run-log", "false");

        HashMap<String, Metric> firstTestMetric = new HashMap<String, Metric>();
        Metric.Builder testMetricBuilder1 = Metric.newBuilder();
        testMetricBuilder1.getMeasurementsBuilder().setSingleString("2.9");
        Metric currentTestMetric = testMetricBuilder1.build();
        firstTestMetric.put("first_test_metric", currentTestMetric);
        mMetricFilePostProcessor.processTestMetricsAndLogs(TEST_1, firstTestMetric, null);

        HashMap<String, Metric> runMetric = new HashMap<String, Metric>();
        Metric.Builder metricBuilder1 = Metric.newBuilder();
        metricBuilder1.getMeasurementsBuilder().setSingleString("2.9");
        Metric currentRunMetric = metricBuilder1.build();
        runMetric.put("run_metric", currentRunMetric);

        mMetricFilePostProcessor.processRunMetricsAndLogs(runMetric, null);

        assertFalse(mTestableMetricUtil.isRunMetricAggregated);
        assertTrue(mTestableMetricUtil.isTestMetricAggregated);
        assertTrue(mTestableMetricUtil.isMetricWrittenToFile);
    }
}
