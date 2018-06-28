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
package com.android.tradefed.testtype.suite.retry;

import static org.mockito.Mockito.verify;

import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationFactory;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.IRescheduler;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.TestRunResult;
import com.android.tradefed.result.suite.SuiteResultHolder;
import com.android.tradefed.testtype.suite.BaseTestSuite;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/** Unit tests for {@link RetryRescheduler}. */
@RunWith(JUnit4.class)
public class RetryReschedulerTest {

    private RetryRescheduler mTest;
    private IConfiguration mTopConfiguration;
    private IConfiguration mRescheduledConfiguration;

    private ITestSuiteResultLoader mMockLoader;
    private IRescheduler mMockRescheduler;
    private IInvocationContext mMockContext;
    private IConfigurationFactory mMockFactory;
    private BaseTestSuite mSuite;

    private SuiteResultHolder mFakeResults;

    @Before
    public void setUp() throws Exception {
        mTest = new RetryRescheduler();
        mTopConfiguration = new Configuration("test", "test");
        mRescheduledConfiguration = EasyMock.createMock(IConfiguration.class);
        mMockLoader = EasyMock.createMock(ITestSuiteResultLoader.class);
        mMockRescheduler = EasyMock.createMock(IRescheduler.class);
        mMockContext = new InvocationContext();
        mMockFactory = EasyMock.createMock(IConfigurationFactory.class);
        mTopConfiguration.setConfigurationObject(
                RetryRescheduler.PREVIOUS_LOADER_NAME, mMockLoader);
        mTest.setConfiguration(mTopConfiguration);
        mTest.setRescheduler(mMockRescheduler);
        mTest.setInvocationContext(mMockContext);
        mTest.setConfigurationFactory(mMockFactory);

        mSuite = Mockito.mock(BaseTestSuite.class);
        EasyMock.expect(mRescheduledConfiguration.getTests()).andStubReturn(Arrays.asList(mSuite));
    }

    /** Test rescheduling a tests that only had pass tests in the first run. */
    @Test
    public void testReschedule_onlyPassTests() throws Exception {
        populateFakeResults(2, 2, 0, 0);
        mMockLoader.init(mMockContext);
        EasyMock.expect(mMockLoader.getCommandLine()).andReturn("previous_command");
        EasyMock.expect(mMockFactory.createConfigurationFromArgs(EasyMock.anyObject()))
                .andReturn(mRescheduledConfiguration);
        EasyMock.expect(mMockLoader.loadPreviousResults()).andReturn(mFakeResults);

        EasyMock.expect(mMockRescheduler.scheduleConfig(mRescheduledConfiguration)).andReturn(true);
        EasyMock.replay(mMockRescheduler, mMockLoader, mMockFactory, mRescheduledConfiguration);
        mTest.run(null);
        EasyMock.verify(mMockRescheduler, mMockLoader, mMockFactory, mRescheduledConfiguration);

        Set<String> excludeRun0 = new HashSet<>();
        excludeRun0.add("run0");
        verify(mSuite).setExcludeFilter(excludeRun0);
        Set<String> excludeRun1 = new HashSet<>();
        excludeRun1.add("run1");
        verify(mSuite).setExcludeFilter(excludeRun1);
    }

    /** Test rescheduling a configuration when some tests previously failed. */
    @Test
    public void testReschedule_someFailedTests() throws Exception {
        populateFakeResults(2, 2, 1, 0);
        mMockLoader.init(mMockContext);
        EasyMock.expect(mMockLoader.getCommandLine()).andReturn("previous_command");
        EasyMock.expect(mMockFactory.createConfigurationFromArgs(EasyMock.anyObject()))
                .andReturn(mRescheduledConfiguration);
        EasyMock.expect(mMockLoader.loadPreviousResults()).andReturn(mFakeResults);

        EasyMock.expect(mMockRescheduler.scheduleConfig(mRescheduledConfiguration)).andReturn(true);
        EasyMock.replay(mMockRescheduler, mMockLoader, mMockFactory, mRescheduledConfiguration);
        mTest.run(null);
        EasyMock.verify(mMockRescheduler, mMockLoader, mMockFactory, mRescheduledConfiguration);
        // Only the passing tests are excluded since we don't want to re-run them
        Set<String> excludeRun0 = new HashSet<>();
        excludeRun0.add("run0 test.class#testPass0");
        verify(mSuite).setExcludeFilter(excludeRun0);
        Set<String> excludeRun1 = new HashSet<>();
        excludeRun1.add("run1 test.class#testPass0");
        verify(mSuite).setExcludeFilter(excludeRun1);
    }

    /**
     * Test rescheduling a configuration when some tests previously failed with assumption failures,
     * these tests will not be re-run.
     */
    @Test
    public void testReschedule_someAssumptionFailures() throws Exception {
        populateFakeResults(2, 2, 0, 1);
        mMockLoader.init(mMockContext);
        EasyMock.expect(mMockLoader.getCommandLine()).andReturn("previous_command");
        EasyMock.expect(mMockFactory.createConfigurationFromArgs(EasyMock.anyObject()))
                .andReturn(mRescheduledConfiguration);
        EasyMock.expect(mMockLoader.loadPreviousResults()).andReturn(mFakeResults);

        EasyMock.expect(mMockRescheduler.scheduleConfig(mRescheduledConfiguration)).andReturn(true);
        EasyMock.replay(mMockRescheduler, mMockLoader, mMockFactory, mRescheduledConfiguration);
        mTest.run(null);
        EasyMock.verify(mMockRescheduler, mMockLoader, mMockFactory, mRescheduledConfiguration);
        // Only the passing tests are excluded since we don't want to re-run them
        Set<String> excludeRun0 = new HashSet<>();
        excludeRun0.add("run0");
        verify(mSuite).setExcludeFilter(excludeRun0);
        Set<String> excludeRun1 = new HashSet<>();
        excludeRun1.add("run1");
        verify(mSuite).setExcludeFilter(excludeRun1);
    }

    /**
     * Test rescheduling a configuration when some mix of failures and assumption failures are
     * present. We reschedule the module with the passed and assumption failure tests excluded.
     */
    @Test
    public void testReschedule_mixedFailedAssumptionFailures() throws Exception {
        populateFakeResults(2, 3, 1, 1);
        mMockLoader.init(mMockContext);
        EasyMock.expect(mMockLoader.getCommandLine()).andReturn("previous_command");
        EasyMock.expect(mMockFactory.createConfigurationFromArgs(EasyMock.anyObject()))
                .andReturn(mRescheduledConfiguration);
        EasyMock.expect(mMockLoader.loadPreviousResults()).andReturn(mFakeResults);

        EasyMock.expect(mMockRescheduler.scheduleConfig(mRescheduledConfiguration)).andReturn(true);
        EasyMock.replay(mMockRescheduler, mMockLoader, mMockFactory, mRescheduledConfiguration);
        mTest.run(null);
        EasyMock.verify(mMockRescheduler, mMockLoader, mMockFactory, mRescheduledConfiguration);
        // Only the passing and assumption failures are excluded
        Set<String> excludeRun0 = new HashSet<>();
        excludeRun0.add("run0 test.class#testPass0");
        verify(mSuite).setExcludeFilter(excludeRun0);
        Set<String> excludeRun0_assume = new HashSet<>();
        excludeRun0_assume.add("run0 test.class#testAssume0");
        verify(mSuite).setExcludeFilter(excludeRun0_assume);

        Set<String> excludeRun1 = new HashSet<>();
        excludeRun1.add("run1 test.class#testPass0");
        verify(mSuite).setExcludeFilter(excludeRun1);
        Set<String> excludeRun1_assume = new HashSet<>();
        excludeRun1_assume.add("run1 test.class#testAssume0");
        verify(mSuite).setExcludeFilter(excludeRun1_assume);
    }

    private void populateFakeResults(
            int numModule, int numTests, int failedTests, int assumpFailure) {
        mFakeResults = new SuiteResultHolder();
        mFakeResults.runResults = new ArrayList<>();
        for (int i = 0; i < numModule; i++) {
            TestRunResult run = new TestRunResult();
            run.testRunStarted("run" + i, numTests);
            for (int j = 0; j < numTests - failedTests - assumpFailure; j++) {
                TestDescription test = new TestDescription("test.class", "testPass" + j);
                run.testStarted(test);
                run.testEnded(test, new HashMap<>());
            }
            for (int f = 0; f < failedTests; f++) {
                TestDescription test = new TestDescription("test.class", "testFail" + f);
                run.testStarted(test);
                run.testFailed(test, "failure" + f);
                run.testEnded(test, new HashMap<>());
            }
            for (int f = 0; f < assumpFailure; f++) {
                TestDescription test = new TestDescription("test.class", "testAssume" + f);
                run.testStarted(test);
                run.testAssumptionFailure(test, "assume" + f);
                run.testEnded(test, new HashMap<>());
            }
            run.testRunEnded(500L, new HashMap<String, Metric>());
            mFakeResults.runResults.add(run);
        }
    }
}
