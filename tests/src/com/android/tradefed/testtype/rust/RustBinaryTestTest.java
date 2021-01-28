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
package com.android.tradefed.testtype.rust;

import static org.junit.Assert.fail;

import com.android.ddmlib.FileListingService;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.MockFileUtil;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.util.FileUtil;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Unit tests for {@link RustBinaryTest}. */
@RunWith(JUnit4.class)
public class RustBinaryTestTest {
    private ITestInvocationListener mMockInvocationListener = null;
    private IShellOutputReceiver mMockReceiver = null;
    private ITestDevice mMockITestDevice = null;
    private RustBinaryTest mRustBinaryTest;
    private TestInformation mTestInfo;
    private Configuration mConfiguration;

    /** Helper to initialize the various EasyMocks we'll need. */
    @Before
    public void setUp() throws Exception {
        mMockInvocationListener = EasyMock.createMock(ITestInvocationListener.class);
        mMockReceiver = EasyMock.createMock(IShellOutputReceiver.class);
        mMockITestDevice = EasyMock.createMock(ITestDevice.class);
        mMockReceiver.flush();
        EasyMock.expectLastCall().anyTimes();
        EasyMock.expect(mMockITestDevice.getSerialNumber()).andStubReturn("serial");
        mRustBinaryTest =
                new RustBinaryTest() {
                    @Override
                    IShellOutputReceiver createParser(
                            ITestInvocationListener listener, String runName) {
                        return mMockReceiver;
                    }
                };
        mRustBinaryTest.setDevice(mMockITestDevice);
        InvocationContext context = new InvocationContext();
        context.addAllocatedDevice("device", mMockITestDevice);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();

        mConfiguration = new Configuration("", "");
        mRustBinaryTest.setConfiguration(mConfiguration);
    }

    /** Helper that replays all mocks. */
    private void replayMocks() {
        EasyMock.replay(mMockInvocationListener, mMockITestDevice, mMockReceiver);
    }

    /** Helper that verifies all mocks. */
    private void verifyMocks() {
        EasyMock.verify(mMockInvocationListener, mMockITestDevice, mMockReceiver);
    }

    /** Add mocked Call "path --list" to count the number of tests. */
    private void mockCountTests(String path, String result) throws DeviceNotAvailableException {
        EasyMock.expect(mMockITestDevice.executeShellCommand(path + " --list")).andReturn(result);
    }

    /** Add mocked call to testRunStarted. */
    private void mockTestRunStarted(String name, int count) {
        mMockInvocationListener.testRunStarted(
                EasyMock.eq(name), EasyMock.eq(count), EasyMock.anyInt(), EasyMock.anyLong());
    }

    /** Add mocked shell command to run a test. */
    private void mockShellCommand(String path) throws DeviceNotAvailableException {
        mMockITestDevice.executeShellCommand(
                EasyMock.contains(path),
                EasyMock.same(mMockReceiver),
                EasyMock.anyLong(),
                (TimeUnit) EasyMock.anyObject(),
                EasyMock.anyInt());
    }

    /** Add mocked call to testRunEnded. */
    private void mockTestRunEnded() {
        mMockInvocationListener.testRunEnded(
                EasyMock.anyLong(), (HashMap<String, Metric>) EasyMock.anyObject());
    }

    /** Call replay/run/verify. */
    private void callReplayRunVerify() throws DeviceNotAvailableException {
        replayMocks();
        mRustBinaryTest.run(mTestInfo, mMockInvocationListener);
        verifyMocks();
    }

    /** Test run when the test dir is not found on the device. */
    @Test
    public void testRun_noTestDir() throws DeviceNotAvailableException {
        String testPath = RustBinaryTest.DEFAULT_TEST_PATH;
        EasyMock.expect(mMockITestDevice.doesFileExist(testPath)).andReturn(false);
        mockTestRunStarted(testPath, 1);
        mMockInvocationListener.testRunFailed("Could not find test directory " + testPath);
        mockTestRunEnded();
        callReplayRunVerify();
    }

    /** Test run when no device is set should throw an exception. */
    @Test
    public void testRun_noDevice() throws DeviceNotAvailableException {
        mRustBinaryTest.setDevice(null);
        replayMocks();
        try {
            mRustBinaryTest.run(mTestInfo, mMockInvocationListener);
            fail("an exception should have been thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
        verifyMocks();
    }

    /** Test the run method for not-found tests */
    @Test
    public void testNotFound() throws DeviceNotAvailableException {
        final String testPath = RustBinaryTest.DEFAULT_TEST_PATH;

        MockFileUtil.setMockDirContents(mMockITestDevice, testPath);
        EasyMock.expect(mMockITestDevice.doesFileExist(testPath)).andReturn(true);
        EasyMock.expect(mMockITestDevice.isDirectory(testPath)).andReturn(true);
        EasyMock.expect(mMockITestDevice.getChildren(testPath)).andReturn(new String[0]);
        mockTestRunStarted(testPath, 1);
        mMockInvocationListener.testRunFailed("No test found under " + testPath);
        mockTestRunEnded();

        callReplayRunVerify();
    }

    /** Test the run method for not-found tests in nested directories */
    @Test
    public void testNotFound2() throws DeviceNotAvailableException {
        final String testPath = RustBinaryTest.DEFAULT_TEST_PATH;
        final String[] dirs = new String[] {"d1", "d2"};
        final String[] d1dirs = new String[] {"d1_1"};
        final String[] nofiles = new String[0];

        MockFileUtil.setMockDirContents(mMockITestDevice, testPath, "d1", "d2");
        MockFileUtil.setMockDirContents(mMockITestDevice, testPath + "/d1", "d1_1");
        EasyMock.expect(mMockITestDevice.doesFileExist(testPath)).andReturn(true);
        EasyMock.expect(mMockITestDevice.isDirectory(testPath)).andReturn(true);
        EasyMock.expect(mMockITestDevice.getChildren(testPath)).andReturn(dirs);
        EasyMock.expect(mMockITestDevice.isDirectory(testPath + "/d1")).andReturn(true);
        EasyMock.expect(mMockITestDevice.getChildren(testPath + "/d1")).andReturn(d1dirs);
        EasyMock.expect(mMockITestDevice.isDirectory(testPath + "/d1/d1_1")).andReturn(true);
        EasyMock.expect(mMockITestDevice.getChildren(testPath + "/d1/d1_1")).andReturn(nofiles);
        EasyMock.expect(mMockITestDevice.isDirectory(testPath + "/d2")).andReturn(true);
        EasyMock.expect(mMockITestDevice.getChildren(testPath + "/d2")).andReturn(nofiles);
        mockTestRunStarted(testPath, 1);
        mMockInvocationListener.testRunFailed("No test found under " + testPath);
        mockTestRunEnded();

        callReplayRunVerify();
    }

    /** Test the run method for a couple tests */
    @Test
    public void testRun() throws DeviceNotAvailableException {
        final String testPath = RustBinaryTest.DEFAULT_TEST_PATH;
        final String test1 = "test1";
        final String test2 = "test2";
        final String testPath1 = String.format("%s/%s", testPath, test1);
        final String testPath2 = String.format("%s/%s", testPath, test2);
        final String[] files = new String[] {test1, test2};

        // Find files
        MockFileUtil.setMockDirContents(mMockITestDevice, testPath, test1, test2);
        EasyMock.expect(mMockITestDevice.doesFileExist(testPath)).andReturn(true);
        EasyMock.expect(mMockITestDevice.isDirectory(testPath)).andReturn(true);
        EasyMock.expect(mMockITestDevice.getChildren(testPath)).andReturn(files);
        EasyMock.expect(mMockITestDevice.isDirectory(testPath1)).andReturn(false);
        EasyMock.expect(mMockITestDevice.isExecutable(testPath1)).andReturn(true);
        EasyMock.expect(mMockITestDevice.isDirectory(testPath2)).andReturn(false);
        EasyMock.expect(mMockITestDevice.isExecutable(testPath2)).andReturn(true);

        mockCountTests(testPath1, "test1\n3 tests, 0 benchmarks\n");
        mockTestRunStarted("test1", 3);
        mockShellCommand(test1);
        mockTestRunEnded();

        mockCountTests(testPath2, "test2\n7 tests, 0 benchmarks\n");
        mockTestRunStarted("test2", 7);
        mockShellCommand(test2);
        mockTestRunEnded();
        callReplayRunVerify();
    }

    /** Test the run method when module name is specified */
    @Test
    public void testRun_moduleName() throws DeviceNotAvailableException {
        final String module = "test1";
        final String modulePath =
                String.format(
                        "%s%s%s",
                        RustBinaryTest.DEFAULT_TEST_PATH,
                        FileListingService.FILE_SEPARATOR,
                        module);
        MockFileUtil.setMockDirContents(mMockITestDevice, modulePath, new String[] {});

        mRustBinaryTest.setModuleName(module);
        EasyMock.expect(mMockITestDevice.doesFileExist(modulePath)).andReturn(true);
        EasyMock.expect(mMockITestDevice.isDirectory(modulePath)).andReturn(false);
        EasyMock.expect(mMockITestDevice.isExecutable(modulePath)).andReturn(true);

        mockCountTests(modulePath, "moduleTest\n1 test, 0 benchmarks\n");
        mockTestRunStarted("test1", 1);
        mockShellCommand(modulePath);
        mockTestRunEnded();
        callReplayRunVerify();
    }

    /** Test the run method for a test in a subdirectory */
    @Test
    public void testRun_nested() throws DeviceNotAvailableException {
        final String testPath = RustBinaryTest.DEFAULT_TEST_PATH;
        final String subFolderName = "subFolder";
        final String subDirPath = testPath + "/" + subFolderName;
        final String test1 = "test1";
        final String test1Path =
                String.format(
                        "%s%s%s%s%s",
                        testPath,
                        FileListingService.FILE_SEPARATOR,
                        subFolderName,
                        FileListingService.FILE_SEPARATOR,
                        test1);
        MockFileUtil.setMockDirPath(mMockITestDevice, testPath, subFolderName, test1);
        EasyMock.expect(mMockITestDevice.doesFileExist(testPath)).andReturn(true);
        EasyMock.expect(mMockITestDevice.isDirectory(testPath)).andReturn(true);
        EasyMock.expect(mMockITestDevice.isDirectory(subDirPath)).andReturn(true);
        EasyMock.expect(mMockITestDevice.isDirectory(test1Path)).andReturn(false);
        // report the file as executable
        EasyMock.expect(mMockITestDevice.isExecutable(test1Path)).andReturn(true);
        String[] files = new String[] {subFolderName};
        EasyMock.expect(mMockITestDevice.getChildren(testPath)).andReturn(files);
        String[] files2 = new String[] {"test1"};
        EasyMock.expect(mMockITestDevice.getChildren(subDirPath)).andReturn(files2);

        mockCountTests(test1Path, "test1\n5 tests, 0 benchmarks\n");
        mockTestRunStarted("test1", 5);
        mockShellCommand(test1Path);
        mockTestRunEnded();
        callReplayRunVerify();
    }

    /**
     * Helper function to do the actual filtering test.
     *
     * @param filterString The string to search for in the Mock, to verify filtering was called
     * @throws DeviceNotAvailableException
     */
    private void doTestFilter(String filterString) throws DeviceNotAvailableException {
        final String testPath = RustBinaryTest.DEFAULT_TEST_PATH;
        final String test1 = "test1";
        final String testPath1 = String.format("%s/%s", testPath, test1);
        final String[] files = new String[] {test1};

        // Find files
        MockFileUtil.setMockDirContents(mMockITestDevice, testPath, test1);
        EasyMock.expect(mMockITestDevice.doesFileExist(testPath)).andReturn(true);
        EasyMock.expect(mMockITestDevice.isDirectory(testPath)).andReturn(true);
        EasyMock.expect(mMockITestDevice.getChildren(testPath)).andReturn(files);
        EasyMock.expect(mMockITestDevice.isDirectory(testPath1)).andReturn(false);
        EasyMock.expect(mMockITestDevice.isExecutable(testPath1)).andReturn(true);

        mockCountTests(testPath1 + filterString, "test1\n3 tests, 0 benchmarks\n");
        mockTestRunStarted("test1", 3);
        mockShellCommand(test1 + filterString);
        mockTestRunEnded();
        callReplayRunVerify();
    }

    /** Test the exclude-filter option. */
    @Test
    public void testExcludeFilter() throws Exception {
        OptionSetter setter = new OptionSetter(mRustBinaryTest);
        setter.setOptionValue("exclude-filter", "NotMe");
        setter.setOptionValue("exclude-filter", "Long");
        doTestFilter(" --skip NotMe --skip Long");
    }

    /** Test both include- and exclude-filter options. */
    @Test
    public void testIncludeExcludeFilter() throws Exception {
        OptionSetter setter = new OptionSetter(mRustBinaryTest);
        setter.setOptionValue("exclude-filter", "NotMe2");
        setter.setOptionValue("include-filter", "OnlyMe");
        setter.setOptionValue("exclude-filter", "other");
        setter.setOptionValue("include-filter", "Me2");
        // Include filters are passed before exclude filters.
        // Multiple include filters are accepted, but all except the 1st are ignored.
        doTestFilter(" OnlyMe Me2 --skip NotMe2 --skip other");
    }
}
