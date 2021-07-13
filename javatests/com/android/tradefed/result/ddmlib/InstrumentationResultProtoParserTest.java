/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tradefed.result.ddmlib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.android.commands.am.InstrumentationData.ResultsBundle;
import com.android.commands.am.InstrumentationData.ResultsBundleEntry;
import com.android.commands.am.InstrumentationData.Session;
import com.android.commands.am.InstrumentationData.SessionStatus;
import com.android.commands.am.InstrumentationData.SessionStatusCode;
import com.android.commands.am.InstrumentationData.TestStatus;
import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.ddmlib.testrunner.TestIdentifier;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/** Unit tests for {@link InstrumentationResultProtoParser}. */
@RunWith(JUnit4.class)
public class InstrumentationResultProtoParserTest {

    private InstrumentationResultProtoParser mParser;
    @Mock ITestRunListener mMockListener;

    private static final String RUN_KEY = "testing";
    private static final String CLASS_NAME_1 = "class_1";
    private static final String METHOD_NAME_1 = "method_1";
    private static final String CLASS_NAME_2 = "class_2";
    private static final String METHOD_NAME_2 = "method_2";
    private static final String TEST_FAILURE_MESSAGE_1 = "java.lang.AssertionError: No App";
    private static final String RUN_FAILURE_MESSAGE = "Unable to find instrumentation info:";
    private static final String TEST_COMPLETED_STATUS_1 = "Expected 2 tests, received 0";
    private static final String TEST_COMPLETED_STATUS_2 = "Expected 2 tests, received 1";
    private static final String INCOMPLETE_TEST_ERR_MSG_PREFIX =
            "Test failed to run" + " to completion";
    private static final String INCOMPLETE_RUN_ERR_MSG_PREFIX = "Test run failed to complete";
    private static final String FATAL_EXCEPTION_MSG = "Fatal exception when running tests";

    private File protoTestFile = null;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        List<ITestRunListener> runListeners = new ArrayList<>();

        runListeners.add(mMockListener);
        mParser = new InstrumentationResultProtoParser(RUN_KEY, runListeners);
    }

    // Sample one test success instrumentation proto file in a test run.

    // result_code: 1
    // results {
    // entries {
    // key: "class"
    // value_string: "android.platform.test.scenario.clock.OpenAppMicrobenchmark"
    // }
    // entries {
    // key: "current"
    // value_int: 1
    // }
    // entries {
    // key: "id"
    // value_string: "AndroidJUnitRunner"
    // }
    // entries {
    // key: "numtests"
    // value_int: 1
    // }
    // entries {
    // key: "stream"
    // value_string: "\nandroid.platform.test.scenario.clock.OpenAppMicrobenchmark:"
    // }
    // entries {
    // key: "test"
    // value_string: "testOpen"
    // }
    // }
    // result_code: 2
    // results {
    // entries {
    // key: "cold_startup_com.google.android.deskclock"
    // value_string: "626"
    // }
    // }
    //
    // results {
    // entries {
    // key: "class"
    // value_string: "android.platform.test.scenario.clock.OpenAppMicrobenchmark"
    // }
    // entries {
    // key: "current"
    // value_int: 1
    // }
    // entries {
    // key: "id"
    // value_string: "AndroidJUnitRunner"
    // }
    // entries {
    // key: "numtests"
    // value_int: 1
    // }
    // entries {
    // key: "stream"
    // value_string: "."
    // }
    // entries {
    // key: "test"
    // value_string: "testOpen"
    // }
    // }
    //
    // result_code: -1
    // results {
    // entries {
    // key: "stream"
    // value_string: "\n\nTime: 27.013\n\nOK (1 test)\n\n"
    // }
    // entries {
    // key: "total_cpu_usage"
    // value_string: "39584"
    // }
    // }

    /**
     * Test for the null input instrumentation results proto file.
     *
     * @throws IOException
     */
    @Test
    public void testNullProtoFile() throws IOException {
        protoTestFile = null;

        mParser.processProtoFile(protoTestFile);

        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener).testRunStarted(RUN_KEY, 0);
        inOrder.verify(mMockListener)
                .testRunFailed(Mockito.eq(InstrumentationResultProtoParser.NO_TEST_RESULTS_FILE));
        inOrder.verify(mMockListener).testRunEnded(0, Collections.emptyMap());

        verify(mMockListener).testRunStarted(RUN_KEY, 0);
        verify(mMockListener)
                .testRunFailed(Mockito.eq(InstrumentationResultProtoParser.NO_TEST_RESULTS_FILE));
        verify(mMockListener).testRunEnded(0, Collections.emptyMap());
    }

    /**
     * Test for the empty input instrumentation results proto file.
     *
     * @throws IOException
     */
    @Test
    public void testEmptyProtoFile() throws IOException {
        protoTestFile = File.createTempFile("tmp", ".pb");

        mParser.processProtoFile(protoTestFile);

        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener).testRunStarted(RUN_KEY, 0);
        inOrder.verify(mMockListener)
                .testRunFailed(Mockito.eq(InstrumentationResultProtoParser.NO_TEST_RESULTS_MSG));
        inOrder.verify(mMockListener).testRunEnded(0, Collections.emptyMap());

        verify(mMockListener).testRunStarted(RUN_KEY, 0);
        verify(mMockListener)
                .testRunFailed(Mockito.eq(InstrumentationResultProtoParser.NO_TEST_RESULTS_MSG));
        verify(mMockListener).testRunEnded(0, Collections.emptyMap());
    }

    /**
     * Test for the invalid input instrumentation results proto file.
     *
     * @throws IOException
     */
    @Test
    public void testInvalidResultsProtoFile() throws IOException {
        protoTestFile = File.createTempFile("tmp", ".pb");
        FileOutputStream fout = new FileOutputStream(protoTestFile);
        fout.write(65);
        fout.close();

        mParser.processProtoFile(protoTestFile);

        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener).testRunStarted(RUN_KEY, 0);
        inOrder.verify(mMockListener)
                .testRunFailed(
                        Mockito.eq(InstrumentationResultProtoParser.INVALID_TEST_RESULTS_FILE));
        inOrder.verify(mMockListener).testRunEnded(0, Collections.emptyMap());

        verify(mMockListener).testRunStarted(RUN_KEY, 0);
        verify(mMockListener)
                .testRunFailed(
                        Mockito.eq(InstrumentationResultProtoParser.INVALID_TEST_RESULTS_FILE));
        verify(mMockListener).testRunEnded(0, Collections.emptyMap());
    }

    /**
     * Test for the no test results in input instrumentation results proto file.
     *
     * @throws IOException
     */
    @Test
    public void testNoTestResults() throws IOException {

        protoTestFile = buildNoTestResultsProtoFile();

        mParser.processProtoFile(protoTestFile);

        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener).testRunStarted(RUN_KEY, 0);
        inOrder.verify(mMockListener).testRunEnded(27013, Collections.emptyMap());

        verify(mMockListener).testRunStarted(RUN_KEY, 0);
        verify(mMockListener).testRunEnded(27013, Collections.emptyMap());
    }

    /**
     * Test for one test success results in input instrumentation results proto file.
     *
     * @throws IOException
     */
    @Test
    public void testOneTestSuccessWithMetrics() throws IOException {
        protoTestFile = buildSingleTestMetricSuccessProtoFile();

        TestIdentifier td = new TestIdentifier(CLASS_NAME_1, METHOD_NAME_1);
        ArgumentCaptor<Map<String, String>> captureTestMetrics = ArgumentCaptor.forClass(Map.class);

        mParser.processProtoFile(protoTestFile);

        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener).testRunStarted(RUN_KEY, 1);
        inOrder.verify(mMockListener).testStarted(td);
        inOrder.verify(mMockListener).testEnded(Mockito.eq(td), captureTestMetrics.capture());
        inOrder.verify(mMockListener).testRunEnded(27013, Collections.emptyMap());

        verify(mMockListener).testRunStarted(RUN_KEY, 1);
        verify(mMockListener).testStarted(td);
        verify(mMockListener).testEnded(Mockito.eq(td), captureTestMetrics.capture());
        verify(mMockListener).testRunEnded(27013, Collections.emptyMap());

        // Verify the test metrics
        assertEquals("626", captureTestMetrics.getValue().get("metric_key1"));
        assertEquals("1", captureTestMetrics.getValue().get("metric_key2"));
    }

    /**
     * Test for one test success result with multiple listeners in instrumentation results proto
     * file.
     *
     * @throws IOException
     */
    @Test
    public void testOneTestSuccessWithMultipleListeners() throws IOException {

        List<ITestRunListener> runListeners = new ArrayList<>();
        ITestRunListener mMockListener1 = mock(ITestRunListener.class);
        ITestRunListener mMockListener2 = mock(ITestRunListener.class);
        runListeners.add(mMockListener1);
        runListeners.add(mMockListener2);

        mParser = new InstrumentationResultProtoParser(RUN_KEY, runListeners);

        protoTestFile = buildSingleTestMetricSuccessProtoFile();

        TestIdentifier td = new TestIdentifier(CLASS_NAME_1, METHOD_NAME_1);

        mParser.processProtoFile(protoTestFile);

        InOrder inOrder = Mockito.inOrder(mMockListener1, mMockListener2);
        inOrder.verify(mMockListener1).testRunStarted(RUN_KEY, 1);
        inOrder.verify(mMockListener2).testRunStarted(RUN_KEY, 1);
        inOrder.verify(mMockListener1).testStarted(td);
        inOrder.verify(mMockListener2).testStarted(td);
        inOrder.verify(mMockListener1).testEnded(Mockito.eq(td), Mockito.any(Map.class));
        inOrder.verify(mMockListener2).testEnded(Mockito.eq(td), Mockito.any(Map.class));
        inOrder.verify(mMockListener1).testRunEnded(27013, Collections.emptyMap());
        inOrder.verify(mMockListener2).testRunEnded(27013, Collections.emptyMap());

        verify(mMockListener1).testRunStarted(RUN_KEY, 1);
        verify(mMockListener1).testStarted(td);
        ArgumentCaptor<Map<String, String>> captureListener1Metrics =
                ArgumentCaptor.forClass(Map.class);
        verify(mMockListener1).testEnded(Mockito.eq(td), captureListener1Metrics.capture());
        verify(mMockListener1).testRunEnded(27013, Collections.emptyMap());

        verify(mMockListener2).testRunStarted(RUN_KEY, 1);
        verify(mMockListener2).testStarted(td);
        ArgumentCaptor<Map<String, String>> captureListener2Metrics =
                ArgumentCaptor.forClass(Map.class);
        verify(mMockListener2).testEnded(Mockito.eq(td), captureListener2Metrics.capture());
        verify(mMockListener2).testRunEnded(27013, Collections.emptyMap());

        // Verify the test metrics
        assertEquals("626", captureListener1Metrics.getValue().get("metric_key1"));
        assertEquals("1", captureListener1Metrics.getValue().get("metric_key2"));

        // Verify the test metrics
        assertEquals("626", captureListener2Metrics.getValue().get("metric_key1"));
        assertEquals("1", captureListener2Metrics.getValue().get("metric_key2"));
    }

    /**
     * Test for test run with the metrics.
     *
     * @throws IOException
     */
    @Test
    public void testOneRunSuccessWithMetrics() throws IOException {
        protoTestFile = buildRunMetricSuccessProtoFile();

        TestIdentifier td = new TestIdentifier(CLASS_NAME_1, METHOD_NAME_1);
        ArgumentCaptor<Map<String, String>> captureRunMetrics = ArgumentCaptor.forClass(Map.class);

        mParser.processProtoFile(protoTestFile);

        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener).testRunStarted(RUN_KEY, 1);
        inOrder.verify(mMockListener).testStarted(td);
        inOrder.verify(mMockListener).testEnded(td, Collections.emptyMap());
        inOrder.verify(mMockListener).testRunEnded(Mockito.eq(27013L), captureRunMetrics.capture());

        verify(mMockListener).testRunStarted(RUN_KEY, 1);
        verify(mMockListener).testStarted(td);
        verify(mMockListener).testEnded(td, Collections.emptyMap());
        verify(mMockListener).testRunEnded(Mockito.eq(27013L), captureRunMetrics.capture());

        // Verify run metrics
        assertEquals("39584", captureRunMetrics.getValue().get("run_metric_key"));
    }

    /**
     * Test for test metrics and test run metrics in instrumentation proto file.
     *
     * @throws IOException
     */
    @Test
    public void testOneTestAndRunSuccessWithMetrics() throws IOException {
        protoTestFile = buildTestAndRunMetricSuccessProtoFile();

        TestIdentifier td = new TestIdentifier(CLASS_NAME_1, METHOD_NAME_1);
        ArgumentCaptor<Map<String, String>> captureTestMetrics = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, String>> captureRunMetrics = ArgumentCaptor.forClass(Map.class);

        mParser.processProtoFile(protoTestFile);

        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener).testRunStarted(RUN_KEY, 1);
        inOrder.verify(mMockListener).testStarted(td);
        inOrder.verify(mMockListener).testEnded(Mockito.eq(td), captureTestMetrics.capture());
        inOrder.verify(mMockListener).testRunEnded(Mockito.eq(27013L), captureRunMetrics.capture());

        verify(mMockListener).testRunStarted(RUN_KEY, 1);
        verify(mMockListener).testStarted(td);
        verify(mMockListener).testEnded(Mockito.eq(td), captureTestMetrics.capture());
        verify(mMockListener).testRunEnded(Mockito.eq(27013L), captureRunMetrics.capture());

        // Verify the test metrics
        assertEquals("626", captureTestMetrics.getValue().get("metric_key1"));
        assertEquals("1", captureTestMetrics.getValue().get("metric_key2"));

        // Verify run metrics
        assertEquals("39584", captureRunMetrics.getValue().get("run_metric_key"));
    }

    /**
     * Test for multiple test success with metrics.
     *
     * @throws IOException
     */
    @Test
    public void testMultipleTestSuccessWithMetrics() throws IOException {
        protoTestFile = buildMultipleTestAndRunMetricSuccessProtoFile();

        TestIdentifier td1 = new TestIdentifier(CLASS_NAME_1, METHOD_NAME_1);
        TestIdentifier td2 = new TestIdentifier(CLASS_NAME_2, METHOD_NAME_2);

        ArgumentCaptor<Map<String, String>> captureTest1Metrics =
                ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, String>> captureTest2Metrics =
                ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, String>> captureRunMetrics = ArgumentCaptor.forClass(Map.class);

        mParser.processProtoFile(protoTestFile);

        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener).testRunStarted(RUN_KEY, 2);
        inOrder.verify(mMockListener).testStarted(td1);
        inOrder.verify(mMockListener).testEnded(Mockito.eq(td1), captureTest1Metrics.capture());
        inOrder.verify(mMockListener).testStarted(td2);
        inOrder.verify(mMockListener).testEnded(Mockito.eq(td2), captureTest2Metrics.capture());
        inOrder.verify(mMockListener).testRunEnded(Mockito.eq(27013L), captureRunMetrics.capture());

        verify(mMockListener).testRunStarted(RUN_KEY, 2);
        verify(mMockListener).testStarted(td1);
        verify(mMockListener).testEnded(Mockito.eq(td1), captureTest1Metrics.capture());
        verify(mMockListener).testStarted(td2);
        verify(mMockListener).testEnded(Mockito.eq(td2), captureTest2Metrics.capture());
        verify(mMockListener).testRunEnded(Mockito.eq(27013L), captureRunMetrics.capture());

        // Verify the test1 and test2 metrics
        assertEquals("626", captureTest1Metrics.getValue().get("metric_key1"));
        assertEquals("1", captureTest1Metrics.getValue().get("metric_key2"));
        assertEquals("626", captureTest2Metrics.getValue().get("metric_key1"));
        assertEquals("1", captureTest2Metrics.getValue().get("metric_key2"));

        // Verify run metrics
        assertEquals("39584", captureRunMetrics.getValue().get("run_metric_key"));
    }

    /**
     * Test for one test failure.
     *
     * @throws IOException
     */
    @Test
    public void testOneTestFailure() throws IOException {
        protoTestFile = buildSingleTestFailureProtoFile();

        TestIdentifier td = new TestIdentifier(CLASS_NAME_1, METHOD_NAME_1);
        ArgumentCaptor<Map<String, String>> captureTestMetrics = ArgumentCaptor.forClass(Map.class);

        mParser.processProtoFile(protoTestFile);

        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener).testRunStarted(RUN_KEY, 1);
        inOrder.verify(mMockListener).testStarted(td);
        inOrder.verify(mMockListener)
                .testFailed(Mockito.eq(td), Mockito.eq(TEST_FAILURE_MESSAGE_1));
        inOrder.verify(mMockListener).testEnded(Mockito.eq(td), captureTestMetrics.capture());
        inOrder.verify(mMockListener).testRunEnded(27013, Collections.emptyMap());

        verify(mMockListener).testRunStarted(RUN_KEY, 1);
        verify(mMockListener).testStarted(td);
        verify(mMockListener).testFailed(Mockito.eq(td), Mockito.eq(TEST_FAILURE_MESSAGE_1));
        verify(mMockListener).testEnded(Mockito.eq(td), captureTestMetrics.capture());
        verify(mMockListener).testRunEnded(27013, Collections.emptyMap());

        // Verify the test metrics
        assertEquals("626", captureTestMetrics.getValue().get("metric_key1"));
        assertEquals("1", captureTestMetrics.getValue().get("metric_key2"));
    }

    /**
     * Test for one test pass and one test failure.
     *
     * @throws IOException
     */
    @Test
    public void testOneTestPassOneTestFailure() throws IOException {
        protoTestFile = buildOneTestPassOneTestFailProtoFile();

        TestIdentifier td1 = new TestIdentifier(CLASS_NAME_1, METHOD_NAME_1);
        TestIdentifier td2 = new TestIdentifier(CLASS_NAME_2, METHOD_NAME_2);

        ArgumentCaptor<Map<String, String>> captureTest1Metrics =
                ArgumentCaptor.forClass(Map.class);

        mParser.processProtoFile(protoTestFile);

        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener).testRunStarted(RUN_KEY, 2);
        inOrder.verify(mMockListener).testStarted(td1);
        inOrder.verify(mMockListener).testEnded(Mockito.eq(td1), captureTest1Metrics.capture());
        inOrder.verify(mMockListener).testStarted(td2);
        inOrder.verify(mMockListener)
                .testFailed(Mockito.eq(td2), Mockito.eq(TEST_FAILURE_MESSAGE_1));
        inOrder.verify(mMockListener).testEnded(td2, Collections.emptyMap());
        inOrder.verify(mMockListener).testRunEnded(27013, Collections.emptyMap());

        verify(mMockListener).testRunStarted(RUN_KEY, 2);
        verify(mMockListener).testStarted(td1);
        verify(mMockListener).testEnded(Mockito.eq(td1), captureTest1Metrics.capture());
        verify(mMockListener).testStarted(td2);
        verify(mMockListener).testFailed(Mockito.eq(td2), Mockito.eq(TEST_FAILURE_MESSAGE_1));
        verify(mMockListener).testEnded(td2, Collections.emptyMap());
        verify(mMockListener).testRunEnded(27013, Collections.emptyMap());

        // Verify the test metrics
        assertEquals("626", captureTest1Metrics.getValue().get("metric_key1"));
        assertEquals("1", captureTest1Metrics.getValue().get("metric_key2"));
    }

    /**
     * Test for all tests incomplete in a test run.
     *
     * @throws IOException
     */
    @Test
    public void testAllTestsIncomplete() throws IOException {
        protoTestFile = buildTestsIncompleteProtoFile();
        ArgumentCaptor<String> testOutputErrorMessage = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> runOutputErrorMessage = ArgumentCaptor.forClass(String.class);

        TestIdentifier td1 = new TestIdentifier(CLASS_NAME_1, METHOD_NAME_1);

        mParser.processProtoFile(protoTestFile);

        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener).testRunStarted(RUN_KEY, 2);
        inOrder.verify(mMockListener).testStarted(td1);
        inOrder.verify(mMockListener).testFailed(Mockito.eq(td1), Mockito.any(String.class));
        inOrder.verify(mMockListener).testEnded(td1, Collections.emptyMap());
        inOrder.verify(mMockListener).testRunFailed(Mockito.any(String.class));
        inOrder.verify(mMockListener).testRunEnded(0, Collections.emptyMap());

        verify(mMockListener).testRunStarted(RUN_KEY, 2);
        verify(mMockListener).testStarted(td1);
        verify(mMockListener).testFailed(Mockito.eq(td1), testOutputErrorMessage.capture());
        verify(mMockListener).testEnded(td1, Collections.emptyMap());
        verify(mMockListener).testRunFailed(runOutputErrorMessage.capture());
        verify(mMockListener).testRunEnded(0, Collections.emptyMap());

        assertTrue(testOutputErrorMessage.getValue().contains(INCOMPLETE_TEST_ERR_MSG_PREFIX));
        assertTrue(testOutputErrorMessage.getValue().contains(TEST_COMPLETED_STATUS_1));
        assertTrue(runOutputErrorMessage.getValue().contains(INCOMPLETE_RUN_ERR_MSG_PREFIX));
        assertTrue(runOutputErrorMessage.getValue().contains(TEST_COMPLETED_STATUS_1));
    }

    /**
     * Test for one test complete and another test partial status.
     *
     * @throws IOException
     */
    @Test
    public void testPartialTestsIncomplete() throws IOException {
        protoTestFile = buildPartialTestsIncompleteProtoFile();

        ArgumentCaptor<String> testOutputErrorMessage = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> runOutputErrorMessage = ArgumentCaptor.forClass(String.class);
        TestIdentifier td1 = new TestIdentifier(CLASS_NAME_1, METHOD_NAME_1);
        TestIdentifier td2 = new TestIdentifier(CLASS_NAME_2, METHOD_NAME_2);
        ArgumentCaptor<Map<String, String>> captureTest1Metrics =
                ArgumentCaptor.forClass(Map.class);

        mParser.processProtoFile(protoTestFile);

        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener).testRunStarted(RUN_KEY, 2);
        inOrder.verify(mMockListener).testStarted(td1);
        inOrder.verify(mMockListener).testEnded(Mockito.eq(td1), Mockito.any(Map.class));
        inOrder.verify(mMockListener).testStarted(td2);
        inOrder.verify(mMockListener).testFailed(Mockito.eq(td2), Mockito.any(String.class));
        inOrder.verify(mMockListener).testEnded(td2, Collections.emptyMap());
        inOrder.verify(mMockListener).testRunFailed(Mockito.any(String.class));
        inOrder.verify(mMockListener).testRunEnded(0, Collections.emptyMap());

        verify(mMockListener).testRunStarted(RUN_KEY, 2);
        verify(mMockListener).testStarted(td1);
        verify(mMockListener).testEnded(Mockito.eq(td1), captureTest1Metrics.capture());
        verify(mMockListener).testStarted(td2);
        verify(mMockListener).testFailed(Mockito.eq(td2), testOutputErrorMessage.capture());
        verify(mMockListener).testEnded(td2, Collections.emptyMap());
        verify(mMockListener).testRunFailed(runOutputErrorMessage.capture());
        verify(mMockListener).testRunEnded(0, Collections.emptyMap());

        assertEquals("626", captureTest1Metrics.getValue().get("metric_key1"));
        assertEquals("1", captureTest1Metrics.getValue().get("metric_key2"));
        assertTrue(testOutputErrorMessage.getValue().contains(INCOMPLETE_TEST_ERR_MSG_PREFIX));
        assertTrue(testOutputErrorMessage.getValue().contains(TEST_COMPLETED_STATUS_2));
        assertTrue(runOutputErrorMessage.getValue().contains(INCOMPLETE_RUN_ERR_MSG_PREFIX));
        assertTrue(runOutputErrorMessage.getValue().contains(TEST_COMPLETED_STATUS_2));
    }

    /**
     * Test 1 test completed, 1 test not started from two expected tests in a test run.
     *
     * @throws IOException
     */
    @Test
    public void testOneTestNotStarted() throws IOException {
        protoTestFile = buildOneTestNotStarted();
        ArgumentCaptor<String> runOutputErrorMessage = ArgumentCaptor.forClass(String.class);
        TestIdentifier td1 = new TestIdentifier(CLASS_NAME_1, METHOD_NAME_1);
        ArgumentCaptor<Map<String, String>> captureTest1Metrics =
                ArgumentCaptor.forClass(Map.class);

        mParser.processProtoFile(protoTestFile);

        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener).testRunStarted(RUN_KEY, 2);
        inOrder.verify(mMockListener).testStarted(td1);
        inOrder.verify(mMockListener).testEnded(Mockito.eq(td1), Mockito.any(Map.class));
        inOrder.verify(mMockListener).testRunFailed(Mockito.any(String.class));
        inOrder.verify(mMockListener).testRunEnded(0, Collections.emptyMap());

        verify(mMockListener).testRunStarted(RUN_KEY, 2);
        verify(mMockListener).testStarted(td1);
        verify(mMockListener).testEnded(Mockito.eq(td1), captureTest1Metrics.capture());
        verify(mMockListener).testRunFailed(runOutputErrorMessage.capture());
        verify(mMockListener).testRunEnded(0, Collections.emptyMap());

        assertEquals("626", captureTest1Metrics.getValue().get("metric_key1"));
        assertEquals("1", captureTest1Metrics.getValue().get("metric_key2"));
        assertTrue(runOutputErrorMessage.getValue().contains(INCOMPLETE_RUN_ERR_MSG_PREFIX));
        assertTrue(runOutputErrorMessage.getValue().contains(TEST_COMPLETED_STATUS_2));
    }

    /**
     * Test for no time stamp parsing error when the time stamp parsing is not enforced.
     *
     * @throws IOException
     */
    @Test
    public void testTimeStampMissingNotEnforced() throws IOException {
        protoTestFile = buildInvalidTimeStampResultsProto(false);

        mParser.processProtoFile(protoTestFile);

        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener).testRunStarted(RUN_KEY, 0);
        inOrder.verify(mMockListener).testRunEnded(0, Collections.emptyMap());

        verify(mMockListener).testRunStarted(RUN_KEY, 0);
        verify(mMockListener).testRunEnded(0, Collections.emptyMap());
    }

    /**
     * Tests parsing the fatal error output of an instrumentation invoked with "-e log true". Since
     * it is log only, it will not report directly the failure, but the stream should still be
     * populated.
     *
     * @throws IOException
     */
    @Test
    public void testDirectFailure() throws IOException {
        protoTestFile = buildValidTimeStampWithFatalExceptionResultsProto();

        ArgumentCaptor<String> capture = ArgumentCaptor.forClass(String.class);

        mParser.processProtoFile(protoTestFile);

        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener).testRunStarted(RUN_KEY, 0);
        inOrder.verify(mMockListener).testRunFailed(capture.capture());
        inOrder.verify(mMockListener).testRunEnded(0, Collections.emptyMap());

        verify(mMockListener).testRunStarted(RUN_KEY, 0);
        verify(mMockListener).testRunFailed(capture.capture());
        verify(mMockListener).testRunEnded(0, Collections.emptyMap());

        String failure = capture.getValue();
        assertTrue(failure.contains("java.lang.RuntimeException: it failed super fast."));
    }

    /**
     * Tests for ignore test status from the proto output.
     *
     * @throws IOException
     */
    @Test
    public void testIgnoreProtoResult() throws IOException {
        protoTestFile = buildTestIgnoredResultsProto();

        TestIdentifier td1 = new TestIdentifier(CLASS_NAME_1, METHOD_NAME_1);

        mParser.processProtoFile(protoTestFile);

        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener).testRunStarted(RUN_KEY, 1);
        inOrder.verify(mMockListener).testStarted(td1);
        inOrder.verify(mMockListener).testIgnored(td1);
        inOrder.verify(mMockListener).testEnded(td1, Collections.emptyMap());
        inOrder.verify(mMockListener).testRunEnded(27013, Collections.emptyMap());

        verify(mMockListener).testRunStarted(RUN_KEY, 1);
        verify(mMockListener).testStarted(td1);
        verify(mMockListener).testIgnored(td1);
        verify(mMockListener).testEnded(td1, Collections.emptyMap());
        verify(mMockListener).testRunEnded(27013, Collections.emptyMap());
    }

    /**
     * Tests for assumption failure test status from the proto output.
     *
     * @throws IOException
     */
    @Test
    public void testAssumptionProtoResult() throws IOException {
        protoTestFile = buildTestAssumptionResultsProto();

        TestIdentifier td1 = new TestIdentifier(CLASS_NAME_1, METHOD_NAME_1);

        mParser.processProtoFile(protoTestFile);

        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener).testRunStarted(RUN_KEY, 1);
        inOrder.verify(mMockListener).testStarted(td1);
        inOrder.verify(mMockListener)
                .testAssumptionFailure(
                        Mockito.eq(td1),
                        Mockito.startsWith(
                                "org.junit.AssumptionViolatedException:"
                                        + " got: <false>, expected: is <true>"));
        inOrder.verify(mMockListener).testEnded(td1, Collections.emptyMap());
        inOrder.verify(mMockListener).testRunEnded(27013, Collections.emptyMap());

        verify(mMockListener).testRunStarted(RUN_KEY, 1);
        verify(mMockListener).testStarted(td1);
        verify(mMockListener)
                .testAssumptionFailure(
                        Mockito.eq(td1),
                        Mockito.startsWith(
                                "org.junit.AssumptionViolatedException:"
                                        + " got: <false>, expected: is <true>"));
        verify(mMockListener).testEnded(td1, Collections.emptyMap());
        verify(mMockListener).testRunEnded(27013, Collections.emptyMap());
    }

    @After
    public void tearDown() {
        if (protoTestFile != null && protoTestFile.exists()) {
            protoTestFile.delete();
        }
    }

    private File buildNoTestResultsProtoFile() throws IOException {
        Session sessionProto =
                Session.newBuilder().setSessionStatus(getSessionStatusProto(false, false)).build();
        File protoFile = File.createTempFile("tmp", ".pb");
        sessionProto.writeTo(new FileOutputStream(protoFile));
        return protoFile;
    }

    private File buildSingleTestMetricSuccessProtoFile() throws IOException {
        List<TestStatus> testStatusList = new LinkedList<TestStatus>();
        // Test start
        testStatusList.add(getTestInfoProto(CLASS_NAME_1, METHOD_NAME_1, 1, 1, true, false));
        // Test Metric
        testStatusList.add(getTestStatusProto(true));
        // Test End
        testStatusList.add(getTestInfoProto(CLASS_NAME_1, METHOD_NAME_1, 1, 1, false, false));
        Session sessionProto =
                Session.newBuilder()
                        .addAllTestStatus(testStatusList)
                        .setSessionStatus(getSessionStatusProto(false, false))
                        .build();
        File protoFile = File.createTempFile("tmp", ".pb");
        sessionProto.writeTo(new FileOutputStream(protoFile));
        return protoFile;
    }

    private File buildRunMetricSuccessProtoFile() throws IOException {
        List<TestStatus> testStatusList = new LinkedList<TestStatus>();
        // Test start.
        testStatusList.add(getTestInfoProto(CLASS_NAME_1, METHOD_NAME_1, 1, 1, true, false));
        // Test status without metrics.
        testStatusList.add(getTestStatusProto(false));
        // Test End.
        testStatusList.add(getTestInfoProto(CLASS_NAME_1, METHOD_NAME_1, 1, 1, false, false));
        // Session with metrics.
        Session sessionProto =
                Session.newBuilder()
                        .addAllTestStatus(testStatusList)
                        .setSessionStatus(getSessionStatusProto(true, false))
                        .build();
        File protoFile = File.createTempFile("tmp", ".pb");
        sessionProto.writeTo(new FileOutputStream(protoFile));
        return protoFile;
    }

    private File buildTestAndRunMetricSuccessProtoFile() throws IOException {
        List<TestStatus> testStatusList = new LinkedList<TestStatus>();
        // Test start.
        testStatusList.add(getTestInfoProto(CLASS_NAME_1, METHOD_NAME_1, 1, 1, true, false));
        // Test status without metrics.
        testStatusList.add(getTestStatusProto(true));
        // Test End.
        testStatusList.add(getTestInfoProto(CLASS_NAME_1, METHOD_NAME_1, 1, 1, false, false));
        // Session with metrics.
        Session sessionProto =
                Session.newBuilder()
                        .addAllTestStatus(testStatusList)
                        .setSessionStatus(getSessionStatusProto(true, false))
                        .build();
        File protoFile = File.createTempFile("tmp", ".pb");
        sessionProto.writeTo(new FileOutputStream(protoFile));
        return protoFile;
    }

    private File buildMultipleTestAndRunMetricSuccessProtoFile() throws IOException {
        List<TestStatus> testStatusList = new LinkedList<TestStatus>();
        // Test start.
        testStatusList.add(getTestInfoProto(CLASS_NAME_1, METHOD_NAME_1, 1, 2, true, false));
        // Test status without metrics.
        testStatusList.add(getTestStatusProto(true));
        // Test End.
        testStatusList.add(getTestInfoProto(CLASS_NAME_1, METHOD_NAME_1, 1, 2, false, false));
        // Test start.
        testStatusList.add(getTestInfoProto(CLASS_NAME_2, METHOD_NAME_2, 2, 2, true, false));
        // Test status without metrics.
        testStatusList.add(getTestStatusProto(true));
        // Test End.
        testStatusList.add(getTestInfoProto(CLASS_NAME_2, METHOD_NAME_2, 2, 2, false, false));
        // Session with metrics.
        Session sessionProto =
                Session.newBuilder()
                        .addAllTestStatus(testStatusList)
                        .setSessionStatus(getSessionStatusProto(true, false))
                        .build();
        File protoFile = File.createTempFile("tmp", ".pb");
        sessionProto.writeTo(new FileOutputStream(protoFile));
        return protoFile;
    }

    private File buildSingleTestFailureProtoFile() throws IOException {
        List<TestStatus> testStatusList = new LinkedList<TestStatus>();
        // Test start.
        testStatusList.add(getTestInfoProto(CLASS_NAME_1, METHOD_NAME_1, 1, 1, true, false));
        // Test status without metrics.
        testStatusList.add(getTestStatusProto(true));
        // Test End.
        testStatusList.add(getTestInfoProto(CLASS_NAME_1, METHOD_NAME_1, 1, 1, false, true));
        // Session with metrics.
        Session sessionProto =
                Session.newBuilder()
                        .addAllTestStatus(testStatusList)
                        .setSessionStatus(getSessionStatusProto(false, false))
                        .build();
        File protoFile = File.createTempFile("tmp", ".pb");
        sessionProto.writeTo(new FileOutputStream(protoFile));
        return protoFile;
    }

    private File buildOneTestPassOneTestFailProtoFile() throws IOException {
        List<TestStatus> testStatusList = new LinkedList<TestStatus>();
        // Test start.
        testStatusList.add(getTestInfoProto(CLASS_NAME_1, METHOD_NAME_1, 1, 2, true, false));
        // Test status without metrics.
        testStatusList.add(getTestStatusProto(true));
        // Test End.
        testStatusList.add(getTestInfoProto(CLASS_NAME_1, METHOD_NAME_1, 1, 2, false, false));
        testStatusList.add(getTestInfoProto(CLASS_NAME_2, METHOD_NAME_2, 2, 2, true, false));
        // Test status without metrics.
        testStatusList.add(getTestStatusProto(false));
        // Test End.
        testStatusList.add(getTestInfoProto(CLASS_NAME_2, METHOD_NAME_2, 2, 2, false, true));
        // Session with metrics.
        Session sessionProto =
                Session.newBuilder()
                        .addAllTestStatus(testStatusList)
                        .setSessionStatus(getSessionStatusProto(false, false))
                        .build();
        File protoFile = File.createTempFile("tmp", ".pb");
        sessionProto.writeTo(new FileOutputStream(protoFile));
        return protoFile;
    }

    private File buildTestsIncompleteProtoFile() throws IOException {
        List<TestStatus> testStatusList = new LinkedList<TestStatus>();
        // Test start.
        testStatusList.add(getTestInfoProto(CLASS_NAME_1, METHOD_NAME_1, 1, 2, true, false));

        // Session with metrics.
        Session sessionProto = Session.newBuilder().addAllTestStatus(testStatusList).build();
        File protoFile = File.createTempFile("tmp", ".pb");
        sessionProto.writeTo(new FileOutputStream(protoFile));
        return protoFile;
    }

    private File buildPartialTestsIncompleteProtoFile() throws IOException {

        List<TestStatus> testStatusList = new LinkedList<TestStatus>();
        // Test start.
        testStatusList.add(getTestInfoProto(CLASS_NAME_1, METHOD_NAME_1, 1, 2, true, false));
        // Test status without metrics.
        testStatusList.add(getTestStatusProto(true));
        // Test End.
        testStatusList.add(getTestInfoProto(CLASS_NAME_1, METHOD_NAME_1, 1, 2, false, false));
        // Test start.
        testStatusList.add(getTestInfoProto(CLASS_NAME_2, METHOD_NAME_2, 2, 2, true, false));

        // Session with metrics.
        Session sessionProto = Session.newBuilder().addAllTestStatus(testStatusList).build();
        File protoFile = File.createTempFile("tmp", ".pb");
        sessionProto.writeTo(new FileOutputStream(protoFile));
        return protoFile;
    }

    private File buildOneTestNotStarted() throws IOException {

        List<TestStatus> testStatusList = new LinkedList<TestStatus>();
        // Test start.
        testStatusList.add(getTestInfoProto(CLASS_NAME_1, METHOD_NAME_1, 1, 2, true, false));
        // Test status without metrics.
        testStatusList.add(getTestStatusProto(true));
        // Test End.
        testStatusList.add(getTestInfoProto(CLASS_NAME_1, METHOD_NAME_1, 1, 2, false, false));

        // Session with metrics.
        Session sessionProto = Session.newBuilder().addAllTestStatus(testStatusList).build();
        File protoFile = File.createTempFile("tmp", ".pb");
        sessionProto.writeTo(new FileOutputStream(protoFile));
        return protoFile;
    }

    private File buildInvalidTimeStampResultsProto(boolean isWithStack) throws IOException {

        List<ResultsBundleEntry> entryList = new LinkedList<ResultsBundleEntry>();

        if (isWithStack) {
            entryList.add(
                    ResultsBundleEntry.newBuilder()
                            .setKey("stream")
                            .setValueString(
                                    FATAL_EXCEPTION_MSG
                                            + " java.lang.IllegalArgumentException: Ambiguous"
                                            + " arguments: cannot provide both test package and"
                                            + " test class(es) to run")
                            .build());
        } else {
            entryList.add(
                    ResultsBundleEntry.newBuilder().setKey("stream").setValueString("").build());
        }

        SessionStatus sessionStatus =
                SessionStatus.newBuilder()
                        .setResultCode(-1)
                        .setStatusCode(SessionStatusCode.SESSION_FINISHED)
                        .setResults(ResultsBundle.newBuilder().addAllEntries(entryList).build())
                        .build();

        // Session with metrics.
        Session sessionProto = Session.newBuilder().setSessionStatus(sessionStatus).build();
        File protoFile = File.createTempFile("tmp", ".pb");
        sessionProto.writeTo(new FileOutputStream(protoFile));
        return protoFile;
    }

    private File buildValidTimeStampWithFatalExceptionResultsProto() throws IOException {
        List<ResultsBundleEntry> entryList = new LinkedList<ResultsBundleEntry>();

        entryList.add(
                ResultsBundleEntry.newBuilder()
                        .setKey("stream")
                        .setValueString(
                                FATAL_EXCEPTION_MSG
                                        + "Time: 0 \n"
                                        + "1) Fatal exception when running tests"
                                        + "java.lang.RuntimeException: it failed super fast."
                                        + "at stackstack")
                        .build());

        SessionStatus sessionStatus =
                SessionStatus.newBuilder()
                        .setResultCode(-1)
                        .setStatusCode(SessionStatusCode.SESSION_FINISHED)
                        .setResults(ResultsBundle.newBuilder().addAllEntries(entryList).build())
                        .build();

        // Session with metrics.
        Session sessionProto = Session.newBuilder().setSessionStatus(sessionStatus).build();
        File protoFile = File.createTempFile("tmp", ".pb");
        sessionProto.writeTo(new FileOutputStream(protoFile));
        return protoFile;
    }

    private File buildTestIgnoredResultsProto() throws IOException {

        List<TestStatus> testStatusList = new LinkedList<TestStatus>();
        // Test start
        testStatusList.add(getTestInfoProto(CLASS_NAME_1, METHOD_NAME_1, 1, 1, true, false));

        // Test ignore status result.
        List<ResultsBundleEntry> entryList = new LinkedList<ResultsBundleEntry>();
        entryList.add(
                ResultsBundleEntry.newBuilder()
                        .setKey("class")
                        .setValueString(CLASS_NAME_1)
                        .build());
        entryList.add(ResultsBundleEntry.newBuilder().setKey("current").setValueInt(1).build());
        entryList.add(
                ResultsBundleEntry.newBuilder()
                        .setKey("id")
                        .setValueString("AndroidJUnitRunner")
                        .build());
        entryList.add(ResultsBundleEntry.newBuilder().setKey("numtests").setValueInt(1).build());

        entryList.add(
                ResultsBundleEntry.newBuilder()
                        .setKey("test")
                        .setValueString(METHOD_NAME_1)
                        .build());

        testStatusList.add(
                TestStatus.newBuilder()
                        .setResultCode(-3)
                        .setResults(ResultsBundle.newBuilder().addAllEntries(entryList).build())
                        .build());

        Session sessionProto =
                Session.newBuilder()
                        .addAllTestStatus(testStatusList)
                        .setSessionStatus(getSessionStatusProto(false, false))
                        .build();
        File protoFile = File.createTempFile("tmp", ".pb");
        sessionProto.writeTo(new FileOutputStream(protoFile));
        return protoFile;
    }

    private File buildTestAssumptionResultsProto() throws IOException {

        List<TestStatus> testStatusList = new LinkedList<TestStatus>();

        // Test start
        testStatusList.add(getTestInfoProto(CLASS_NAME_1, METHOD_NAME_1, 1, 1, true, false));

        // Test ignore status result.
        List<ResultsBundleEntry> entryList = new LinkedList<ResultsBundleEntry>();
        entryList.add(
                ResultsBundleEntry.newBuilder()
                        .setKey("class")
                        .setValueString(CLASS_NAME_1)
                        .build());
        entryList.add(ResultsBundleEntry.newBuilder().setKey("current").setValueInt(1).build());
        entryList.add(
                ResultsBundleEntry.newBuilder()
                        .setKey("id")
                        .setValueString("AndroidJUnitRunner")
                        .build());
        entryList.add(ResultsBundleEntry.newBuilder().setKey("numtests").setValueInt(1).build());
        entryList.add(
                ResultsBundleEntry.newBuilder()
                        .setKey("test")
                        .setValueString(METHOD_NAME_1)
                        .build());
        entryList.add(
                ResultsBundleEntry.newBuilder()
                        .setKey("stack")
                        .setValueString(
                                "org.junit.AssumptionViolatedException: got: <false>, expected: is"
                                        + " <true>")
                        .build());
        testStatusList.add(
                TestStatus.newBuilder()
                        .setResultCode(-4)
                        .setResults(ResultsBundle.newBuilder().addAllEntries(entryList).build())
                        .build());

        Session sessionProto =
                Session.newBuilder()
                        .addAllTestStatus(testStatusList)
                        .setSessionStatus(getSessionStatusProto(false, false))
                        .build();
        File protoFile = File.createTempFile("tmp", ".pb");
        sessionProto.writeTo(new FileOutputStream(protoFile));
        return protoFile;
    }

    /**
     * Add test status proto message based on the args supplied to this method.
     *
     * @param className class name where the test method is.
     * @param methodName method name currently running.
     * @param current current number of the test.
     * @param numTests total number of test.
     * @param isStart true is if it is start of the test otherwise treated as end of the test.
     * @param isFailure true if the test if failed.
     * @return
     */
    private TestStatus getTestInfoProto(
            String className,
            String methodName,
            int current,
            int numTests,
            boolean isStart,
            boolean isFailure) {
        List<ResultsBundleEntry> entryList = new LinkedList<ResultsBundleEntry>();
        entryList.add(
                ResultsBundleEntry.newBuilder().setKey("class").setValueString(className).build());
        entryList.add(
                ResultsBundleEntry.newBuilder().setKey("current").setValueInt(current).build());
        entryList.add(
                ResultsBundleEntry.newBuilder()
                        .setKey("id")
                        .setValueString("AndroidJUnitRunner")
                        .build());
        entryList.add(
                ResultsBundleEntry.newBuilder().setKey("numtests").setValueInt(numTests).build());

        entryList.add(
                ResultsBundleEntry.newBuilder().setKey("test").setValueString(methodName).build());

        if (isFailure) {
            entryList.add(
                    ResultsBundleEntry.newBuilder()
                            .setKey("stack")
                            .setValueString(TEST_FAILURE_MESSAGE_1)
                            .build());
            entryList.add(
                    ResultsBundleEntry.newBuilder()
                            .setKey("stream")
                            .setValueString(TEST_FAILURE_MESSAGE_1)
                            .build());
            // Test failure will have result code "-2"
            return TestStatus.newBuilder()
                    .setResultCode(-2)
                    .setResults(ResultsBundle.newBuilder().addAllEntries(entryList).build())
                    .build();
        }

        entryList.add(
                ResultsBundleEntry.newBuilder().setKey("stream").setValueString("\nabc:").build());

        if (isStart) {
            // Test start will have result code 1.
            return TestStatus.newBuilder()
                    .setResultCode(1)
                    .setResults(ResultsBundle.newBuilder().addAllEntries(entryList).build())
                    .build();
        }

        return TestStatus.newBuilder()
                .setResults(ResultsBundle.newBuilder().addAllEntries(entryList).build())
                .build();
    }

    /**
     * Add test status with the metrics in the proto result file.
     *
     * @param isWithMetrics if false metric will be ignored.
     * @return
     */
    private TestStatus getTestStatusProto(boolean isWithMetrics) {
        List<ResultsBundleEntry> entryList = new LinkedList<ResultsBundleEntry>();
        if (isWithMetrics) {
            entryList.add(
                    ResultsBundleEntry.newBuilder()
                            .setKey("metric_key1")
                            .setValueString("626")
                            .build());
            entryList.add(
                    ResultsBundleEntry.newBuilder()
                            .setKey("metric_key2")
                            .setValueString("1")
                            .build());
        }

        // Metric status will be in progress
        return TestStatus.newBuilder()
                .setResultCode(2)
                .setResults(ResultsBundle.newBuilder().addAllEntries(entryList).build())
                .build();
    }

    /**
     * Add session status message in the proto result file based on the args supplied to this
     * method.
     *
     * @param isWithMetrics is true then add metrics to the session message.
     * @param isFailure is true then failure message will be added to the final message.
     * @return
     */
    private SessionStatus getSessionStatusProto(boolean isWithMetrics, boolean isFailure) {
        List<ResultsBundleEntry> entryList = new LinkedList<ResultsBundleEntry>();

        if (isFailure) {
            entryList.add(
                    ResultsBundleEntry.newBuilder()
                            .setKey("Error")
                            .setValueString(RUN_FAILURE_MESSAGE)
                            .build());
            entryList.add(
                    ResultsBundleEntry.newBuilder()
                            .setKey("id")
                            .setValueString("ActivityManagerService")
                            .build());
            return SessionStatus.newBuilder()
                    .setResultCode(-1)
                    .setStatusCode(SessionStatusCode.SESSION_FINISHED)
                    .setResults(ResultsBundle.newBuilder().addAllEntries(entryList).build())
                    .build();
        }
        entryList.add(
                ResultsBundleEntry.newBuilder()
                        .setKey("stream")
                        .setValueString("\n\nTime: 27.013\n\nOK (1 test)\n\n")
                        .build());

        if (isWithMetrics) {
            entryList.add(
                    ResultsBundleEntry.newBuilder()
                            .setKey("run_metric_key")
                            .setValueString("39584")
                            .build());
        }

        return SessionStatus.newBuilder()
                .setResultCode(-1)
                .setStatusCode(SessionStatusCode.SESSION_FINISHED)
                .setResults(ResultsBundle.newBuilder().addAllEntries(entryList).build())
                .build();
    }
}
