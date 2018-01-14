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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit test for {@link DeviceMetricData} * */
@RunWith(JUnit4.class)
public class DeviceMetricDataTest {

    @Test
    public void testAddStringMetric_success() {
        DeviceMetricData deviceMetricData = new DeviceMetricData();
        deviceMetricData.addStringMetric("key", "value");

        Map<String, String> actualData = new HashMap<>();
        deviceMetricData.addToMetrics(actualData);

        assertEquals(1, actualData.size());
        assertTrue(actualData.containsKey("key"));
        assertEquals(actualData.get("key"), "value");
    }

    @Test(expected = NullPointerException.class)
    public void testAddToMetrics_Fail() {
        DeviceMetricData deviceMetricData = new DeviceMetricData();
        deviceMetricData.addStringMetric("key", "value");

        // Let the asked data to remain null which should cause the Preconditions check to fail.
        Map<String, String> actualData = null;
        deviceMetricData.addToMetrics(actualData);
    }

    @Test
    public void testAddToMetricsMultiThreaded_success()
            throws InterruptedException, ExecutionException {
        // Incrementing threadCounts in steps and then testing makes sure that there is no
        // flakyness, sticking to one value of threadCount will cause flakyness.
        for (int threadCount = 10; threadCount <= 200; threadCount += 10) {
            testAddToMetricsMultiThreaded(threadCount);
        }
    }

    private void testAddToMetricsMultiThreaded(int threadCount)
            throws InterruptedException, ExecutionException {
        // Create the object to test.
        DeviceMetricData deviceMetricData = new DeviceMetricData();

        // Create a callable wrapper of DeviceMetricData#addStringMetric and
        // DeviceMetricData#addToMetrics which will add a metric and then try to retrieve it.
        Callable<Map<String, String>> task =
                new Callable<Map<String, String>>() {

                    @Override
                    public Map<String, String> call() throws Exception {
                        deviceMetricData.addStringMetric(UUID.randomUUID().toString(), "value");
                        Map<String, String> data = new HashMap<>();
                        deviceMetricData.addToMetrics(data);
                        return data;
                    }
                };
        // Create a copy of this callable for every thread.
        List<Callable<Map<String, String>>> tasks = Collections.nCopies(threadCount, task);

        // Create a thread pool to execute the tasks.
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

        // Invoke the tasks. The call to ExecutorService#invokeAll blocks until all the threads are
        // done.
        List<Future<Map<String, String>>> futures = executorService.invokeAll(tasks);

        // Store the results from all the tasks in a common data structure.
        Map<String, String> metricsData = new HashMap<String, String>(futures.size());
        for (Future<Map<String, String>> future : futures) {
            metricsData.putAll(future.get());
        }

        // assert that the number of metrics out is equal to number of metrics in.
        assertEquals(threadCount, metricsData.size());

        // discard all the threads.
        executorService.shutdownNow();
    }
}
