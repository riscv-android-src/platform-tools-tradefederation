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

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.Measurements;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;

import com.google.common.collect.ListMultimap;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.DoubleSummaryStatistics;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A metric aggregator that gives the min, max, mean, variance and standard deviation for numeric
 * metrics collected during multiple-iteration test runs, treating them as doubles. Non-numeric
 * metrics are ignored.
 *
 * <p>It parses metrics from single string as currently metrics are passed this way.
 */
public class AggregatePostProcessor extends BasePostProcessor {
    private static final String STATS_KEY_MIN = "min";
    private static final String STATS_KEY_MAX = "max";
    private static final String STATS_KEY_MEAN = "mean";
    private static final String STATS_KEY_VAR = "var";
    private static final String STATS_KEY_STDEV = "stdev";
    // Separator for final upload
    private static final String STATS_KEY_SEPARATOR = "-";

    @Override
    public Map<String, Metric.Builder> processRunMetrics(HashMap<String, Metric> rawMetrics) {
        return new HashMap<String, Metric.Builder>();
    }

    @Override
    public Map<String, Metric.Builder> processAllTestMetrics(
            ListMultimap<String, Metric> allTestMetrics) {
        // Aggregate final test metrics.
        Map<String, Metric.Builder> aggregateMetrics = new HashMap<String, Metric.Builder>();
        for (String key : allTestMetrics.keySet()) {
            List<Metric> metrics = allTestMetrics.get(key);
            List<Measurements> measures =
                    metrics.stream().map(Metric::getMeasurements).collect(Collectors.toList());
            // Parse metrics into a list of SingleString values, concating lists in the process
            List<String> rawValues =
                    measures.stream()
                            .map(Measurements::getSingleString)
                            .map(
                                    m -> {
                                        // Split results; also deals with the case of empty results
                                        // in a certain run
                                        List<String> splitVals = Arrays.asList(m.split(",", 0));
                                        if (splitVals.size() == 1 && splitVals.get(0).isEmpty()) {
                                            return Collections.<String>emptyList();
                                        }
                                        return splitVals;
                                    })
                            .flatMap(Collection::stream)
                            .map(String::trim)
                            .collect(Collectors.toList());
            // Do not report empty metrics
            if (rawValues.isEmpty()) {
                continue;
            }
            boolean areAllDoubles =
                    rawValues
                            .stream()
                            .allMatch(
                                    val -> {
                                        try {
                                            Double.parseDouble(val);
                                            return true;
                                        } catch (NumberFormatException e) {
                                            return false;
                                        }
                                    });
            if (areAllDoubles) {
                List<Double> values =
                        rawValues.stream().map(Double::parseDouble).collect(Collectors.toList());
                HashMap<String, Double> stats = getStats(values);
                for (String statKey : stats.keySet()) {
                    Metric.Builder metricBuilder = Metric.newBuilder();
                    metricBuilder
                            .getMeasurementsBuilder()
                            .setSingleString(String.format("%2.2f", stats.get(statKey)));
                    aggregateMetrics.put(
                            String.join(STATS_KEY_SEPARATOR, key, statKey), metricBuilder);
                }
            } else {
                CLog.i("Metric %s is not numeric", key);
            }
        }
        // Ignore the passed-in run metrics.
        return aggregateMetrics;
    }

    private HashMap<String, Double> getStats(Iterable<Double> values) {
        HashMap<String, Double> stats = new HashMap<>();
        DoubleSummaryStatistics summaryStats = new DoubleSummaryStatistics();
        for (Double value : values) {
            summaryStats.accept(value);
        }
        Double mean = summaryStats.getAverage();
        Double count = Long.valueOf(summaryStats.getCount()).doubleValue();
        Double variance = (double) 0;
        for (Double value : values) {
            variance += Math.pow(value - mean, 2) / count;
        }
        stats.put(STATS_KEY_MIN, summaryStats.getMin());
        stats.put(STATS_KEY_MAX, summaryStats.getMax());
        stats.put(STATS_KEY_MEAN, mean);
        stats.put(STATS_KEY_VAR, variance);
        stats.put(STATS_KEY_STDEV, Math.sqrt(variance));
        return stats;
    }
}
