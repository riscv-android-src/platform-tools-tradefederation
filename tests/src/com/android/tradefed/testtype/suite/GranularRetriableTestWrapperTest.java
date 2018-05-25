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
package com.android.tradefed.testtype.suite;

import com.android.ddmlib.testrunner.TestResult.TestStatus;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceUnresponsiveException;
import com.android.tradefed.device.metric.IMetricCollector;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.FileSystemLogSaver;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.TestResult;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Unit tests for {@link com.android.tradefed.testtype.suite.GranularRetriableTestWrapper}. */
@RunWith(JUnit4.class)
public class GranularRetriableTestWrapperTest {

    private class FakeTest implements IRemoteTest {
        private TestDescription mTestCase;

        @Override
        public void run(ITestInvocationListener listener) {
            mTestCase = new TestDescription("ClassFoo", "TestFoo");
            listener.testRunStarted("test run", 1);
            listener.testStarted(mTestCase);
            listener.testEnded(mTestCase, Collections.emptyMap());
            listener.testRunEnded(0, new HashMap<String, Metric>());
        }
    }

    public GranularRetriableTestWrapper createGranularTestWrapper(IRemoteTest test) {
        GranularRetriableTestWrapper granularTestWrapper =
                new GranularRetriableTestWrapper(test, null, null);
        granularTestWrapper.setModuleId("test module");
        granularTestWrapper.setMarkTestsSkipped(false);
        granularTestWrapper.setMetricCollectors(new ArrayList<IMetricCollector>());
        // Setup InvocationContext.
        granularTestWrapper.setInvocationContext(new InvocationContext());
        // Setup logsaver.
        granularTestWrapper.setLogSaver(new FileSystemLogSaver());
        IConfiguration mockModuleConfiguration = Mockito.mock(IConfiguration.class);
        granularTestWrapper.setModuleConfig(mockModuleConfiguration);
        return granularTestWrapper;
    }

    // @Before
    // public void setUp() {

    /** Test the generic workflow of run method. */
    @Test
    public void testRun_FullPassVerify() throws Exception {
        FakeTest fakeTest = new FakeTest();
        GranularRetriableTestWrapper granularTestWrapper = createGranularTestWrapper(fakeTest);
        // Schedule to run the test 3 times.
        granularTestWrapper.run(new CollectingTestListener(), 3);
        assertEquals(1, granularTestWrapper.getNumIndividualTests());
        // Verify that if three test runs are scheduled, we should have three TestRunResult,
        // and the MetricCollector should be only initialized once.
        assertEquals(3, granularTestWrapper.getFinalTestRunResults().size());
        Map<TestDescription, TestResult> runResultMap =
                granularTestWrapper.getFinalTestRunResults().get(0).getTestResults();
        assertTrue(runResultMap.containsKey(fakeTest.mTestCase));
        assertEquals(TestStatus.PASSED, runResultMap.get(fakeTest.mTestCase).getStatus());
    }

    /**
     * Test that the "run" method catches DeviceNotAvailableException and raises it after record the
     * tests.
     */
    @Test(expected = DeviceNotAvailableException.class)
    public void testRun_catchDeviceNotAvailableException() throws Exception {
        IRemoteTest mockTest = Mockito.mock(IRemoteTest.class);
        Mockito.doThrow(new DeviceNotAvailableException("fake message", "serial"))
                .when(mockTest)
                .run(Mockito.any(ITestInvocationListener.class));
        GranularRetriableTestWrapper granularTestWrapper = createGranularTestWrapper(mockTest);
        // Verify.
        granularTestWrapper.run(new CollectingTestListener());
    }

    /**
     * Test that the "run" method catches DeviceUnresponsiveException and doesn't raise it again.
     */
    @Test
    public void testRun_catchDeviceUnresponsiveException() throws Exception {
        IRemoteTest mockTest = Mockito.mock(IRemoteTest.class);
        Mockito.doThrow(new DeviceUnresponsiveException("fake message", "serial"))
                .when(mockTest)
                .run(Mockito.any(ITestInvocationListener.class));
        ModuleListener mockRunListener = Mockito.mock(ModuleListener.class);
        Mockito.doNothing().when(mockRunListener).testRunFailed(Mockito.anyString());

        GranularRetriableTestWrapper granularTestWrapper = createGranularTestWrapper(mockTest);
        GranularRetriableTestWrapper spyGranularTestWrapper = Mockito.spy(granularTestWrapper);

        Mockito.doReturn(mockRunListener).when(spyGranularTestWrapper).createModuleListener();
        // Verify.
        spyGranularTestWrapper.run(Mockito.any(ITestInvocationListener.class));
        Mockito.verify(mockTest, times(1)).run(Mockito.any(ITestInvocationListener.class));
        Mockito.verify(mockRunListener, times(1)).testRunFailed(Mockito.anyString());
    }
}
