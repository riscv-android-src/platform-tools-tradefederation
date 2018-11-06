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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;

import java.util.Map;

/** Unit tests for {@link AggregatePostProcessor} */
@RunWith(JUnit4.class)
public class AggregatePostProcessorTest {

    private static final String TEST_CLASS = "test.class";
    private static final String TEST_NAME = "test.name";

    private static final Integer TEST_ITERATIONS = 3;

    // Upload key suffixes for each aggregate metric
    private static final String STATS_KEY_MIN = "min";
    private static final String STATS_KEY_MAX = "max";
    private static final String STATS_KEY_MEAN = "mean";
    private static final String STATS_KEY_VAR = "var";
    private static final String STATS_KEY_STDEV = "stdev";
    // Separator for final upload
    private static final String STATS_KEY_SEPARATOR = "-";

    private AggregatePostProcessor mCollector;

    @Before
    public void setUp() {
        mCollector = new AggregatePostProcessor();
    }

    /** Test corrrect aggregation of singular double metrics. */
    @Test
    public void testSingularDoubleMetric() {
        // Singular double metrics test: Sample results and expected aggregate metric values.
        final String singularDoubleKey = "singular_double";
        final ImmutableList<String> singularDoubleMetrics = ImmutableList.of("1.1", "2", "2.9");
        final ImmutableMap<String, String> singularDoubleStats =
                ImmutableMap.of(
                        STATS_KEY_MIN, "1.10",
                        STATS_KEY_MAX, "2.90",
                        STATS_KEY_MEAN, "2.00",
                        STATS_KEY_VAR, "0.54",
                        STATS_KEY_STDEV, "0.73");

        // Construct ListMultimap of multiple iterations of test metrics.
        ListMultimap<String, Metric> allTestMetrics = ArrayListMultimap.create();
        for (Integer i = 0; i < TEST_ITERATIONS; i++) {
            Metric.Builder metricBuilder = Metric.newBuilder();
            metricBuilder.getMeasurementsBuilder().setSingleString(singularDoubleMetrics.get(i));
            Metric currentTestMetric = metricBuilder.build();
            allTestMetrics.put(singularDoubleKey, currentTestMetric);
        }

        // Test that the correct aggregate metrics are returned.
        Map<String, Metric.Builder> aggregateMetrics =
                mCollector.processAllTestMetrics(allTestMetrics);

        Assert.assertTrue(
                aggregateMetrics.containsKey(
                        String.join(STATS_KEY_SEPARATOR, singularDoubleKey, STATS_KEY_MIN)));
        Assert.assertEquals(
                singularDoubleStats.get(STATS_KEY_MIN),
                aggregateMetrics
                        .get(String.join(STATS_KEY_SEPARATOR, singularDoubleKey, STATS_KEY_MIN))
                        .build()
                        .getMeasurements()
                        .getSingleString());
        Assert.assertTrue(
                aggregateMetrics.containsKey(
                        String.join(STATS_KEY_SEPARATOR, singularDoubleKey, STATS_KEY_MAX)));
        Assert.assertEquals(
                singularDoubleStats.get(STATS_KEY_MAX),
                aggregateMetrics
                        .get(String.join(STATS_KEY_SEPARATOR, singularDoubleKey, STATS_KEY_MAX))
                        .build()
                        .getMeasurements()
                        .getSingleString());
        Assert.assertTrue(
                aggregateMetrics.containsKey(
                        String.join(STATS_KEY_SEPARATOR, singularDoubleKey, STATS_KEY_MEAN)));
        Assert.assertEquals(
                singularDoubleStats.get(STATS_KEY_MEAN),
                aggregateMetrics
                        .get(String.join(STATS_KEY_SEPARATOR, singularDoubleKey, STATS_KEY_MEAN))
                        .build()
                        .getMeasurements()
                        .getSingleString());
        Assert.assertTrue(
                aggregateMetrics.containsKey(
                        String.join(STATS_KEY_SEPARATOR, singularDoubleKey, STATS_KEY_VAR)));
        Assert.assertEquals(
                singularDoubleStats.get(STATS_KEY_VAR),
                aggregateMetrics
                        .get(String.join(STATS_KEY_SEPARATOR, singularDoubleKey, STATS_KEY_VAR))
                        .build()
                        .getMeasurements()
                        .getSingleString());
        Assert.assertTrue(
                aggregateMetrics.containsKey(
                        String.join(STATS_KEY_SEPARATOR, singularDoubleKey, STATS_KEY_STDEV)));
        Assert.assertEquals(
                singularDoubleStats.get(STATS_KEY_STDEV),
                aggregateMetrics
                        .get(String.join(STATS_KEY_SEPARATOR, singularDoubleKey, STATS_KEY_STDEV))
                        .build()
                        .getMeasurements()
                        .getSingleString());
    }

    /** Test correct aggregation of list double metrics. */
    @Test
    public void testListDoubleMetric() {
        // List double metrics test: Sample results and expected aggregate metric values.
        final String listDoubleKey = "list_double";
        final ImmutableList<String> listDoubleMetrics =
                ImmutableList.of("1.1, 2.2", "", "1.5, 2.5, 1.9, 2.9");
        final ImmutableMap<String, String> listDoubleStats =
                ImmutableMap.of(
                        STATS_KEY_MIN, "1.10",
                        STATS_KEY_MAX, "2.90",
                        STATS_KEY_MEAN, "2.02",
                        STATS_KEY_VAR, "0.36",
                        STATS_KEY_STDEV, "0.60");

        // Construct ListMultimap of multiple iterations of test metrics.
        ListMultimap<String, Metric> allTestMetrics = ArrayListMultimap.create();
        for (Integer i = 0; i < TEST_ITERATIONS; i++) {
            Metric.Builder metricBuilder = Metric.newBuilder();
            metricBuilder.getMeasurementsBuilder().setSingleString(listDoubleMetrics.get(i));
            Metric currentTestMetric = metricBuilder.build();
            allTestMetrics.put(listDoubleKey, currentTestMetric);
        }

        // Test that the correct aggregate metrics are returned.
        Map<String, Metric.Builder> aggregateMetrics =
                mCollector.processAllTestMetrics(allTestMetrics);

        Assert.assertTrue(
                aggregateMetrics.containsKey(
                        String.join(STATS_KEY_SEPARATOR, listDoubleKey, STATS_KEY_MIN)));
        Assert.assertEquals(
                listDoubleStats.get(STATS_KEY_MIN),
                aggregateMetrics
                        .get(listDoubleKey + STATS_KEY_SEPARATOR + STATS_KEY_MIN)
                        .build()
                        .getMeasurements()
                        .getSingleString());
        Assert.assertTrue(
                aggregateMetrics.containsKey(listDoubleKey + STATS_KEY_SEPARATOR + STATS_KEY_MAX));
        Assert.assertEquals(
                listDoubleStats.get(STATS_KEY_MAX),
                aggregateMetrics
                        .get(listDoubleKey + STATS_KEY_SEPARATOR + STATS_KEY_MAX)
                        .build()
                        .getMeasurements()
                        .getSingleString());
        Assert.assertTrue(
                aggregateMetrics.containsKey(listDoubleKey + STATS_KEY_SEPARATOR + STATS_KEY_MEAN));
        Assert.assertEquals(
                listDoubleStats.get(STATS_KEY_MEAN),
                aggregateMetrics
                        .get(listDoubleKey + STATS_KEY_SEPARATOR + STATS_KEY_MEAN)
                        .build()
                        .getMeasurements()
                        .getSingleString());
        Assert.assertTrue(
                aggregateMetrics.containsKey(listDoubleKey + STATS_KEY_SEPARATOR + STATS_KEY_VAR));
        Assert.assertEquals(
                listDoubleStats.get(STATS_KEY_VAR),
                aggregateMetrics
                        .get(listDoubleKey + STATS_KEY_SEPARATOR + STATS_KEY_VAR)
                        .build()
                        .getMeasurements()
                        .getSingleString());
        Assert.assertTrue(
                aggregateMetrics.containsKey(
                        listDoubleKey + STATS_KEY_SEPARATOR + STATS_KEY_STDEV));
        Assert.assertEquals(
                listDoubleStats.get(STATS_KEY_STDEV),
                aggregateMetrics
                        .get(listDoubleKey + STATS_KEY_SEPARATOR + STATS_KEY_STDEV)
                        .build()
                        .getMeasurements()
                        .getSingleString());
    }

    /** Test that non-numeric metric does not show up in the reported results. */
    @Test
    public void testNonNumericMetric() {
        // Non-numeric metrics test: Sample results; should not show up in aggregate metrics
        final String nonNumericKey = "non_numeric";
        final ImmutableList<String> nonNumericMetrics = ImmutableList.of("1", "success", "failed");

        // Construct ListMultimap of multiple iterations of test metrics.
        ListMultimap<String, Metric> allTestMetrics = ArrayListMultimap.create();
        for (Integer i = 0; i < TEST_ITERATIONS; i++) {
            Metric.Builder metricBuilder = Metric.newBuilder();
            metricBuilder.getMeasurementsBuilder().setSingleString(nonNumericMetrics.get(i));
            Metric currentTestMetric = metricBuilder.build();
            allTestMetrics.put(nonNumericKey, currentTestMetric);
        }

        // Test that non-numeric metrics do not get returned.
        Map<String, Metric.Builder> aggregateMetrics =
                mCollector.processAllTestMetrics(allTestMetrics);

        Assert.assertFalse(
                aggregateMetrics.containsKey(
                        String.join(STATS_KEY_SEPARATOR, nonNumericKey, STATS_KEY_MIN)));
        Assert.assertFalse(
                aggregateMetrics.containsKey(
                        String.join(STATS_KEY_SEPARATOR, nonNumericKey, STATS_KEY_MAX)));
        Assert.assertFalse(
                aggregateMetrics.containsKey(
                        String.join(STATS_KEY_SEPARATOR, nonNumericKey, STATS_KEY_MEAN)));
        Assert.assertFalse(
                aggregateMetrics.containsKey(
                        String.join(STATS_KEY_SEPARATOR, nonNumericKey, STATS_KEY_VAR)));
        Assert.assertFalse(
                aggregateMetrics.containsKey(
                        String.join(STATS_KEY_SEPARATOR, nonNumericKey, STATS_KEY_STDEV)));
    }

    /** Test empty result. */
    @Test
    public void testEmptyResult() {
        final String emptyResultKey = "empty_result";

        // Construct ListMultimap of multiple iterations of test metrics.
        ListMultimap<String, Metric> allTestMetrics = ArrayListMultimap.create();
        for (Integer i = 0; i < TEST_ITERATIONS; i++) {
            Metric.Builder metricBuilder = Metric.newBuilder();
            metricBuilder.getMeasurementsBuilder().setSingleString("");
            Metric currentTestMetric = metricBuilder.build();
            allTestMetrics.put(emptyResultKey, currentTestMetric);
        }

        // Test that test with empty results do not get returned.
        Map<String, Metric.Builder> aggregateMetrics =
                mCollector.processAllTestMetrics(allTestMetrics);

        Assert.assertFalse(
                aggregateMetrics.containsKey(
                        String.join(STATS_KEY_SEPARATOR, emptyResultKey, STATS_KEY_MIN)));
        Assert.assertFalse(
                aggregateMetrics.containsKey(
                        String.join(STATS_KEY_SEPARATOR, emptyResultKey, STATS_KEY_MAX)));
        Assert.assertFalse(
                aggregateMetrics.containsKey(
                        String.join(STATS_KEY_SEPARATOR, emptyResultKey, STATS_KEY_MEAN)));
        Assert.assertFalse(
                aggregateMetrics.containsKey(
                        String.join(STATS_KEY_SEPARATOR, emptyResultKey, STATS_KEY_VAR)));
        Assert.assertFalse(
                aggregateMetrics.containsKey(
                        String.join(STATS_KEY_SEPARATOR, emptyResultKey, STATS_KEY_STDEV)));
    }

    /** Test single run. */
    @Test
    public void testSingleRun() {
        final String singleRunKey = "single_run";
        final String singleRunVal = "1.00";
        final String zeroStr = "0.00";

        // Construct ListMultimap of a single iteration of test metrics.
        ListMultimap<String, Metric> allTestMetrics = ArrayListMultimap.create();
        Metric.Builder metricBuilder = Metric.newBuilder();
        metricBuilder.getMeasurementsBuilder().setSingleString(singleRunVal);
        Metric currentTestMetric = metricBuilder.build();
        allTestMetrics.put(singleRunKey, currentTestMetric);

        // Test that single runs still give the correct aggregate metrics.
        Map<String, Metric.Builder> aggregateMetrics =
                mCollector.processAllTestMetrics(allTestMetrics);

        Assert.assertTrue(
                aggregateMetrics.containsKey(
                        String.join(STATS_KEY_SEPARATOR, singleRunKey, STATS_KEY_MIN)));
        Assert.assertEquals(
                singleRunVal,
                aggregateMetrics
                        .get(String.join(STATS_KEY_SEPARATOR, singleRunKey, STATS_KEY_MIN))
                        .build()
                        .getMeasurements()
                        .getSingleString());
        Assert.assertTrue(
                aggregateMetrics.containsKey(
                        String.join(STATS_KEY_SEPARATOR, singleRunKey, STATS_KEY_MAX)));
        Assert.assertEquals(
                singleRunVal,
                aggregateMetrics
                        .get(String.join(STATS_KEY_SEPARATOR, singleRunKey, STATS_KEY_MAX))
                        .build()
                        .getMeasurements()
                        .getSingleString());
        Assert.assertTrue(
                aggregateMetrics.containsKey(
                        String.join(STATS_KEY_SEPARATOR, singleRunKey, STATS_KEY_MEAN)));
        Assert.assertEquals(
                singleRunVal,
                aggregateMetrics
                        .get(String.join(STATS_KEY_SEPARATOR, singleRunKey, STATS_KEY_MEAN))
                        .build()
                        .getMeasurements()
                        .getSingleString());
        Assert.assertTrue(
                aggregateMetrics.containsKey(
                        String.join(STATS_KEY_SEPARATOR, singleRunKey, STATS_KEY_VAR)));
        Assert.assertEquals(
                zeroStr,
                aggregateMetrics
                        .get(String.join(STATS_KEY_SEPARATOR, singleRunKey, STATS_KEY_VAR))
                        .build()
                        .getMeasurements()
                        .getSingleString());
        Assert.assertTrue(
                aggregateMetrics.containsKey(
                        String.join(STATS_KEY_SEPARATOR, singleRunKey, STATS_KEY_STDEV)));
        Assert.assertEquals(
                zeroStr,
                aggregateMetrics
                        .get(String.join(STATS_KEY_SEPARATOR, singleRunKey, STATS_KEY_STDEV))
                        .build()
                        .getMeasurements()
                        .getSingleString());
    }

    /** Test zero runs. */
    @Test
    public void testZeroRun() {
        // Test that tests with zero runs do not get added to the processed metrics.
        ListMultimap<String, Metric> allTestMetrics = ArrayListMultimap.create();
        Map<String, Metric.Builder> aggregateMetrics =
                mCollector.processAllTestMetrics(allTestMetrics);

        Assert.assertEquals(0, aggregateMetrics.keySet().size());
    }
}
