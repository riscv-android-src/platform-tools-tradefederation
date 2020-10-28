/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tradefed.testtype;

import static org.junit.Assert.fail;

import com.android.ddmlib.CollectingOutputReceiver;

import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import org.easymock.EasyMock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link ArtRunTest}. */
@RunWith(JUnit4.class)
public class ArtRunTestTest {

    private ITestInvocationListener mMockInvocationListener;
    private IAbi mMockAbi;
    private ITestDevice mMockITestDevice;

    private CollectingOutputReceiver mOutputReceiver;
    private ArtRunTest mArtRunTest;
    private OptionSetter mSetter;
    private TestInformation mTestInfo;
    // Test dependencies directory on host.
    private File mTmpDepsDir;
    // Expected output file (within the dependencies directory).
    private File mTmpExpectedFile;

    @Before
    public void setUp() throws ConfigurationException, IOException {
        mMockInvocationListener = EasyMock.createMock(ITestInvocationListener.class);
        mMockAbi = EasyMock.createMock(IAbi.class);
        mMockITestDevice = EasyMock.createMock(ITestDevice.class);
        mOutputReceiver = new CollectingOutputReceiver();
        mArtRunTest =
                new ArtRunTest() {
                    @Override
                    protected CollectingOutputReceiver createTestOutputReceiver() {
                        return mOutputReceiver;
                    }
                };
        mArtRunTest.setAbi(mMockAbi);
        mArtRunTest.setDevice(mMockITestDevice);
        mSetter = new OptionSetter(mArtRunTest);

        // Temporary test directory (e.g. for the expected-output file).
        mTmpDepsDir = FileUtil.createTempDir("art-run-test-deps");
        mTestInfo = TestInformation.newBuilder().setDependenciesFolder(mTmpDepsDir).build();
    }

    @After
    public void tearDown() {
        FileUtil.recursiveDelete(mTmpDepsDir);
    }

    /** Helper creating an expected-output file within the (temporary) test directory. */
    private void createExpectedOutputFile(String runTestName) throws IOException {
        mTmpExpectedFile = new File(mTmpDepsDir, runTestName + "-expected.txt");
        FileWriter fw = new FileWriter(mTmpExpectedFile);
        fw.write("output\n");
        fw.close();
    }

    /** Helper mocking writing the output of a test command. */
    private void mockTestOutputWrite(String output) {
        mOutputReceiver.addOutput(output.getBytes(), 0, output.length());
    }

    /** Helper that replays all mocks. */
    private void replayMocks() {
        EasyMock.replay(mMockInvocationListener, mMockAbi, mMockITestDevice);
    }

    /** Helper that verifies all mocks. */
    private void verifyMocks() {
        EasyMock.verify(mMockInvocationListener, mMockAbi, mMockITestDevice);
    }

    /** Test run when no device is set should throw an exception. */
    @Test
    public void testRun_noDevice() throws DeviceNotAvailableException {
        mArtRunTest.setDevice(null);
        replayMocks();
        try {
            mArtRunTest.run(mTestInfo, mMockInvocationListener);
            fail("An exception should have been thrown.");
        } catch (IllegalArgumentException e) {
            // Expected.
        }
        verifyMocks();
    }

    /** Test the behavior of the run method when the `run-test-name` option is not set. */
    @Test
    public void testRunSingleTest_unsetRunTestNameOption()
            throws ConfigurationException, DeviceNotAvailableException, IOException {
        final String classpath = "/data/local/tmp/test/test.jar";
        mSetter.setOptionValue("classpath", classpath);

        replayMocks();
        try {
            mArtRunTest.run(mTestInfo, mMockInvocationListener);
            fail("An exception should have been thrown.");
        } catch (IllegalArgumentException e) {
            // Expected.
        }
        verifyMocks();
    }

    /** Test the behavior of the run method when the `classpath` option is not set. */
    @Test
    public void testRunSingleTest_unsetClasspathOption()
            throws ConfigurationException, DeviceNotAvailableException, IOException {
        final String runTestName = "test";
        mSetter.setOptionValue("run-test-name", runTestName);
        createExpectedOutputFile(runTestName);

        replayMocks();
        try {
            mArtRunTest.run(mTestInfo, mMockInvocationListener);
            fail("An exception should have been thrown.");
        } catch (IllegalArgumentException e) {
            // Expected.
        }
        verifyMocks();
    }

    /** Helper containing testing logic for a (single) test expected to run (and succeed). */
    private void doTestRunSingleTest(final String runTestName, final String classpath)
            throws ConfigurationException, DeviceNotAvailableException, IOException {
        mSetter.setOptionValue("run-test-name", runTestName);
        createExpectedOutputFile(runTestName);
        mSetter.setOptionValue("classpath", classpath);

        // Pre-test checks.
        EasyMock.expect(mMockAbi.getName()).andReturn("abi");
        EasyMock.expect(mMockITestDevice.getSerialNumber()).andReturn("");
        String runName = "ArtRunTest_abi";
        // Beginning of test.
        mMockInvocationListener.testRunStarted(runName, 1);
        TestDescription testId = new TestDescription(runName, runTestName);
        mMockInvocationListener.testStarted(testId);
        String cmd = String.format("dalvikvm64 -classpath %s Main", classpath);
        // Test execution.
        mMockITestDevice.executeShellCommand(
                cmd, mOutputReceiver, 60000L, TimeUnit.MILLISECONDS, 0);
        mockTestOutputWrite("output\n");
        EasyMock.expect(mMockITestDevice.getSerialNumber()).andReturn("");
        // End of test.
        mMockInvocationListener.testEnded(
                EasyMock.eq(testId), (HashMap<String, Metric>) EasyMock.anyObject());
        mMockInvocationListener.testRunEnded(
                EasyMock.anyLong(), (HashMap<String, Metric>) EasyMock.anyObject());

        replayMocks();

        mArtRunTest.run(mTestInfo, mMockInvocationListener);

        verifyMocks();
    }

    /** Helper containing testing logic for a (single) test expected not to run. */
    private void doTestDoNotRunSingleTest(final String runTestName, final String classpath)
            throws ConfigurationException, DeviceNotAvailableException, IOException {
        mSetter.setOptionValue("run-test-name", runTestName);
        createExpectedOutputFile(runTestName);
        mSetter.setOptionValue("classpath", classpath);

        EasyMock.expect(mMockAbi.getName()).andReturn("abi");
        replayMocks();

        mArtRunTest.run(mTestInfo, mMockInvocationListener);

        verifyMocks();
    }

    /** Test the run method for a (single) test. */
    @Test
    public void testRunSingleTest()
            throws ConfigurationException, DeviceNotAvailableException, IOException {
        final String runTestName = "test";
        final String classpath = "/data/local/tmp/test/test.jar";

        doTestRunSingleTest(runTestName, classpath);
    }

    /**
     * Test the behavior of the run method when the output produced by the shell command on device
     * differs from the expected output.
     */
    @Test
    public void testRunSingleTest_unexpectedOutput()
            throws ConfigurationException, DeviceNotAvailableException, IOException {
        final String runTestName = "test";
        mSetter.setOptionValue("run-test-name", runTestName);
        createExpectedOutputFile(runTestName);
        final String classpath = "/data/local/tmp/test/test.jar";
        mSetter.setOptionValue("classpath", classpath);

        // Pre-test checks.
        EasyMock.expect(mMockAbi.getName()).andReturn("abi");
        EasyMock.expect(mMockITestDevice.getSerialNumber()).andReturn("");
        String runName = "ArtRunTest_abi";
        // Beginning of test.
        mMockInvocationListener.testRunStarted(runName, 1);
        TestDescription testId = new TestDescription(runName, runTestName);
        mMockInvocationListener.testStarted(testId);
        String cmd = String.format("dalvikvm64 -classpath %s Main", classpath);
        // Test execution.
        mMockITestDevice.executeShellCommand(
                cmd, mOutputReceiver, 60000L, TimeUnit.MILLISECONDS, 0);
        mockTestOutputWrite("unexpected\n");
        EasyMock.expect(mMockITestDevice.getSerialNumber()).andReturn("");
        // End of test.
        String errorMessage =
                "The test's standard output does not match the expected output:\n"
                        + "--- expected.txt\n"
                        + "+++ stdout\n"
                        + "@@ -1,1 +1,1 @@\n"
                        + "-output\n"
                        + "+unexpected\n";
        mMockInvocationListener.testFailed(testId, errorMessage);
        mMockInvocationListener.testEnded(
                EasyMock.eq(testId), (HashMap<String, Metric>) EasyMock.anyObject());
        mMockInvocationListener.testRunEnded(
                EasyMock.anyLong(), (HashMap<String, Metric>) EasyMock.anyObject());

        replayMocks();

        mArtRunTest.run(mTestInfo, mMockInvocationListener);

        verifyMocks();
    }

    /** Test the run method for a (single) test contained in an include filter. */
    @Test
    public void testIncludeFilter()
            throws ConfigurationException, DeviceNotAvailableException, IOException {
        final String runTestName = "test";
        final String classpath = "/data/local/tmp/test/test.jar";
        // Add an include filter containing the test's name.
        mArtRunTest.addIncludeFilter(runTestName);

        doTestRunSingleTest(runTestName, classpath);
    }

    /** Test the run method for a (single) test contained in an exclude filter. */
    @Test
    public void testExcludeFilter()
            throws ConfigurationException, DeviceNotAvailableException, IOException {
        final String runTestName = "test";
        final String classpath = "/data/local/tmp/test/test.jar";
        // Add an exclude filter containing the test's name.
        mArtRunTest.addExcludeFilter(runTestName);

        doTestDoNotRunSingleTest(runTestName, classpath);
    }

    /**
     * Test the run method for a (single) test contained both in an include and an exclude filter.
     */
    @Test
    public void testIncludeAndExcludeFilter()
            throws ConfigurationException, DeviceNotAvailableException, IOException {
        final String runTestName = "test";
        final String classpath = "/data/local/tmp/test/test.jar";
        // Add an include filter containing the test's name.
        mArtRunTest.addIncludeFilter(runTestName);
        // Add an exclude filter containing the test's name.
        mArtRunTest.addExcludeFilter(runTestName);

        doTestDoNotRunSingleTest(runTestName, classpath);
    }
}
