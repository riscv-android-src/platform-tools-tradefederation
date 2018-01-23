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
import static org.junit.Assert.fail;

import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.util.RunUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

/** Unit tests for {@link ScheduleMultipleDeviceMetricCollector}. */
@RunWith(JUnit4.class)
public class ScheduleMultipleDeviceMetricCollectorTest {
    @Rule public final TemporaryFolder folder = new TemporaryFolder();
    @Mock private ITestDevice testDevice;
    @Mock private ITestInvocationListener mMockListener;
    @Spy private ScheduleMultipleDeviceMetricCollector multipleMetricCollector;
    private IInvocationContext mContext;

    static class TestMeminfoCollector extends ScheduledDeviceMetricCollector {
        private int mInternalCounter = 0;
        private String key = "meminfo";

        TestMeminfoCollector() {
            setTag("meminfoInterval");
        }

        @Override
        public void collect(DeviceMetricData runData) throws InterruptedException {
            mInternalCounter++;
            runData.addStringMetric(key + mInternalCounter, "value" + mInternalCounter);
        }
    }

    static class TestJankinfoCollector extends ScheduledDeviceMetricCollector {
        private int mInternalCounter = 0;
        private String key = "jankinfo";

        TestJankinfoCollector() {
            setTag("jankInterval");
        }

        @Override
        public void collect(DeviceMetricData runData) throws InterruptedException {
            mInternalCounter++;
            runData.addStringMetric(key + mInternalCounter, "value" + mInternalCounter);
        }
    }

    static class TestFragmentationCollector extends ScheduledDeviceMetricCollector {
        private int mInternalCounter = 0;
        private String key = "fragmentation";

        TestFragmentationCollector() {
            setTag("fragmentationInterval");
        }

        @Override
        public void collect(DeviceMetricData runData) throws InterruptedException {
            mInternalCounter++;
            runData.addStringMetric(key + mInternalCounter, "value" + mInternalCounter);
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContext = new InvocationContext();
    }

    @Test
    public void testMultipleMetricCollector_success() throws Exception {
        OptionSetter setter = new OptionSetter(multipleMetricCollector);

        // Set up the metric collection storage path.
        File metricStoragePath = folder.newFolder();
        setter.setOptionValue("metric-storage-path", metricStoragePath.toString());

        // Set up the intervals.
        Map<String, Long> intervals = new HashMap<>();
        intervals.put("meminfoInterval", 100L);
        intervals.put("fragmentationInterval", 100L);
        intervals.put("jankInterval", 100L);
        for (String key : intervals.keySet()) {
            setter.setOptionValue(
                    "metric-collection-intervals", key, intervals.get(key).toString());
        }

        // Request the collectors.
        List<String> classnames = new ArrayList<>();
        classnames.add(TestMeminfoCollector.class.getName());
        classnames.add(TestJankinfoCollector.class.getName());
        classnames.add(TestFragmentationCollector.class.getName());
        for (String key : classnames) {
            setter.setOptionValue("metric-collector-command-classes", key);
        }

        DeviceMetricData runData = new DeviceMetricData(mContext);

        // Start the tests.
        Map<String, String> metrics = new HashMap<>();
        multipleMetricCollector.init(mContext, mMockListener);
        try {
            multipleMetricCollector.onTestRunStart(runData);
            RunUtil.getDefault().sleep(500);
        } finally {
            multipleMetricCollector.onTestRunEnd(runData, metrics);
        }

        // We give it 500msec to run and 100msec interval we should easily have at least run all the
        // metrics once.
        // assert that the metrics contains filenames of all the collected metrics.
        Map<String, String> metricsCollected = new HashMap<>();
        runData.addToMetrics(metricsCollected);

        assertTrue(metricsCollected.containsKey("jankinfo1"));
        assertTrue(metricsCollected.containsKey("meminfo1"));
        assertTrue(metricsCollected.containsKey("fragmentation1"));
    }

    @Test
    public void testMultipleMetricCollector_noFailureEvenIfNoCollectorRequested() throws Exception {
        Map<String, String> metrics = new HashMap<>();
        multipleMetricCollector.init(mContext, mMockListener);

        DeviceMetricData runData = new DeviceMetricData(mContext);

        try {
            multipleMetricCollector.onTestRunStart(runData);
            RunUtil.getDefault().sleep(500);
        } finally {
            multipleMetricCollector.onTestRunEnd(runData, metrics);
        }

        // No metrics should have been collected.
        Map<String, String> metricsCollected = new HashMap<>();
        runData.addToMetrics(metricsCollected);

        assertEquals(0, metricsCollected.size());
    }

    @Test
    public void testMultipleMetricCollector_failsForNonNegativeInterval() throws Exception {
        String expectedStderr =
                "class com.android.tradefed.device.metric."
                        + "ScheduleMultipleDeviceMetricCollectorTest$TestJankinfoCollector expects "
                        + "a non negative interval.";

        OptionSetter setter = new OptionSetter(multipleMetricCollector);

        // Set up the metric collection storage path.
        setter.setOptionValue("metric-storage-path", folder.newFolder().toString());

        // Set up the interval.
        Map<String, Long> intervals = new HashMap<>();
        intervals.put("jankInterval", -100L);
        for (String key : intervals.keySet()) {
            setter.setOptionValue(
                    "metric-collection-intervals", key, intervals.get(key).toString());
        }

        // Set up the classname.
        List<String> classnames = new ArrayList<>();
        classnames.add(TestJankinfoCollector.class.getName());
        for (String key : classnames) {
            setter.setOptionValue("metric-collector-command-classes", key);
        }

        DeviceMetricData runData = new DeviceMetricData(mContext);

        // Start the tests, which should fail with the expected error message.
        multipleMetricCollector.init(mContext, mMockListener);

        try {
            multipleMetricCollector.onTestRunStart(runData);
            fail("Should throw illegal argument exception in case of negative intervals.");
        } catch (IllegalArgumentException e) {
            assertEquals(expectedStderr, e.getMessage());
        }
    }
}
