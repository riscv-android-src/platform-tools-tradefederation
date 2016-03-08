/*
 * Copyright (C) 2010 The Android Open Source Project
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

import com.android.ddmlib.FileListingService;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.MockFileUtil;
import com.android.tradefed.result.ITestInvocationListener;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.util.concurrent.TimeUnit;


/**
 * Unit tests for {@link GTestTest}.
 */
public class GTestTest extends TestCase {
    private static final String GTEST_FLAG_FILTER = "--gtest_filter";
    private static final String LS_LD_TEST1_OUTPUT =
            "-rw-rw-rw- 1 root root 0 2016-02-19 10:02 /data/nativetest/test1";
    private static final String LS_LD_TEST2_OUTPUT =
            "-rw-rw-rw- 1 root root 0 2016-02-19 10:02 /data/nativetest/test2";
    private static final String LS_LD_NATIVETEST_OUTPUT =
            "drwxrwx--x 4 shell shell 4096 2016-03-07 11:46 /data/nativetest";
    private static final String LS_LD_SUBDIR_OUTPUT =
            "drwxrwx--x 4 shell shell 4096 2016-03-07 11:46 /data/nativetest/subfolder";
    private ITestInvocationListener mMockInvocationListener = null;
    private IShellOutputReceiver mMockReceiver = null;
    private ITestDevice mMockITestDevice = null;
    private GTest mGTest;

    /**
     * Helper to initialize the various EasyMocks we'll need.
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockInvocationListener = EasyMock.createMock(ITestInvocationListener.class);
        mMockReceiver = EasyMock.createMock(IShellOutputReceiver.class);
        mMockITestDevice = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(mMockITestDevice.getSerialNumber()).andStubReturn("serial");
        mGTest = new GTest() {
            @Override
            IShellOutputReceiver createResultParser(String runName, ITestRunListener listener) {
                return mMockReceiver;
            }
        };
        mGTest.setDevice(mMockITestDevice);
    }

    /**
     * Helper that replays all mocks.
     */
    private void replayMocks() {
      EasyMock.replay(mMockInvocationListener, mMockITestDevice, mMockReceiver);
    }

    /**
     * Helper that verifies all mocks.
     */
    private void verifyMocks() {
      EasyMock.verify(mMockInvocationListener, mMockITestDevice, mMockReceiver);
    }

    /**
     * Test the run method for a couple tests
     */
    public void testRun() throws DeviceNotAvailableException {
        final String nativeTestPath = GTest.DEFAULT_NATIVETEST_PATH;
        final String test1 = "test1";
        final String test2 = "test2";

        MockFileUtil.setMockDirContents(mMockITestDevice, nativeTestPath, test1, test2);
        EasyMock.expect(mMockITestDevice.doesFileExist(nativeTestPath)).andReturn(true);
        EasyMock.expect(mMockITestDevice.executeShellCommand(
                "ls -ld /data/nativetest")).andReturn(LS_LD_NATIVETEST_OUTPUT);
        EasyMock.expect(mMockITestDevice.executeShellCommand(
                "ls -ld /data/nativetest/test1")).andReturn(LS_LD_TEST1_OUTPUT);
        EasyMock.expect(mMockITestDevice.executeShellCommand(
                "ls -ld /data/nativetest/test2")).andReturn(LS_LD_TEST2_OUTPUT);
        EasyMock.expect(mMockITestDevice.executeShellCommand(
                "ls -A1 /data/nativetest")).andReturn("test1\ntest2");
        EasyMock.expect(mMockITestDevice.executeShellCommand(EasyMock.contains("chmod")))
                .andReturn("")
                .times(2);
        mMockITestDevice.executeShellCommand(EasyMock.contains(test1),
                EasyMock.same(mMockReceiver), EasyMock.anyLong(),
                (TimeUnit)EasyMock.anyObject(), EasyMock.anyInt());
        mMockITestDevice.executeShellCommand(EasyMock.contains(test2),
                EasyMock.same(mMockReceiver), EasyMock.anyLong(),
                (TimeUnit)EasyMock.anyObject(), EasyMock.anyInt());

        replayMocks();

        mGTest.run(mMockInvocationListener);
        verifyMocks();
    }

    /**
     * Test the run method when module name is specified
     */
    public void testRun_moduleName() throws DeviceNotAvailableException {
        final String module = "test1";
        final String modulePath = String.format("%s%s%s",
                GTest.DEFAULT_NATIVETEST_PATH, FileListingService.FILE_SEPARATOR, module);
        MockFileUtil.setMockDirContents(mMockITestDevice, modulePath, new String[] {});

        mGTest.setModuleName(module);

        EasyMock.expect(mMockITestDevice.doesFileExist(modulePath)).andReturn(true);
        EasyMock.expect(mMockITestDevice.executeShellCommand(
                "ls -ld /data/nativetest/test1")).andReturn(LS_LD_TEST1_OUTPUT);
        // expect test1 to be executed
        EasyMock.expect(mMockITestDevice.executeShellCommand(EasyMock.contains("chmod")))
                .andReturn("");
        mMockITestDevice.executeShellCommand(EasyMock.contains(modulePath),
                EasyMock.same(mMockReceiver),
                EasyMock.anyLong(), (TimeUnit)EasyMock.anyObject(), EasyMock.anyInt());

        replayMocks();

        mGTest.run(mMockInvocationListener);
        verifyMocks();
    }

    /**
     * Test the run method for a test in a subdirectory
     */
    public void testRun_nested() throws DeviceNotAvailableException {
        final String nativeTestPath = GTest.DEFAULT_NATIVETEST_PATH;
        final String subFolderName = "subFolder";
        final String test1 = "test1";
        final String test1Path = String.format("%s%s%s%s%s", nativeTestPath,
                FileListingService.FILE_SEPARATOR,
                subFolderName,
                FileListingService.FILE_SEPARATOR, test1);

        MockFileUtil.setMockDirPath(mMockITestDevice, nativeTestPath, subFolderName, test1);
        EasyMock.expect(mMockITestDevice.doesFileExist(nativeTestPath)).andReturn(true);
        EasyMock.expect(mMockITestDevice.executeShellCommand(
                "ls -ld /data/nativetest")).andReturn(LS_LD_NATIVETEST_OUTPUT);
        EasyMock.expect(mMockITestDevice.executeShellCommand(
                "ls -ld /data/nativetest/" + subFolderName)).andReturn(LS_LD_SUBDIR_OUTPUT);
        EasyMock.expect(mMockITestDevice.executeShellCommand(
                "ls -ld " + test1Path)).andReturn(LS_LD_TEST1_OUTPUT);
        EasyMock.expect(mMockITestDevice.executeShellCommand(
                "ls -A1 /data/nativetest")).andReturn(subFolderName);
        EasyMock.expect(mMockITestDevice.executeShellCommand(
                "ls -A1 /data/nativetest/" + subFolderName)).andReturn("test1");
        EasyMock.expect(mMockITestDevice.executeShellCommand(EasyMock.contains("chmod")))
                .andReturn("");
        mMockITestDevice.executeShellCommand(EasyMock.contains(test1Path),
                EasyMock.same(mMockReceiver),
                EasyMock.anyLong(), (TimeUnit)EasyMock.anyObject(), EasyMock.anyInt());

        replayMocks();

        mGTest.run(mMockInvocationListener);
        verifyMocks();
    }

    /**
     * Helper function to do the actual filtering test.
     *
     * @param filterString The string to search for in the Mock, to verify filtering was called
     * @throws DeviceNotAvailableException
     */
    private void doTestFilter(String filterString) throws DeviceNotAvailableException {
        String nativeTestPath = GTest.DEFAULT_NATIVETEST_PATH;
        // configure the mock file system to have a single test
        MockFileUtil.setMockDirContents(mMockITestDevice, nativeTestPath, "test1");
        EasyMock.expect(mMockITestDevice.doesFileExist(nativeTestPath)).andReturn(true);
        EasyMock.expect(mMockITestDevice.executeShellCommand(
                "ls -ld /data/nativetest")).andReturn(LS_LD_NATIVETEST_OUTPUT);
        EasyMock.expect(mMockITestDevice.executeShellCommand(
                "ls -A1 /data/nativetest")).andReturn("test1");
        EasyMock.expect(mMockITestDevice.executeShellCommand(
                "ls -ld /data/nativetest/test1")).andReturn(LS_LD_TEST1_OUTPUT);
        EasyMock.expect(mMockITestDevice.executeShellCommand(EasyMock.contains("chmod")))
                    .andReturn("");
            mMockITestDevice.executeShellCommand(EasyMock.contains(filterString),
                    EasyMock.same(mMockReceiver),
                    EasyMock.anyLong(), (TimeUnit)EasyMock.anyObject(), EasyMock.anyInt());
        replayMocks();
        mGTest.run(mMockInvocationListener);

        verifyMocks();
    }

    /**
     * Test the include filtering of test methods.
     */
    public void testIncludeFilter() throws DeviceNotAvailableException {
        String includeFilter1 = "abc";
        String includeFilter2 = "def";
        mGTest.addIncludeFilter(includeFilter1);
        mGTest.addIncludeFilter(includeFilter2);
        doTestFilter(String.format("%s=%s:%s", GTEST_FLAG_FILTER, includeFilter1, includeFilter2));
    }

    /**
     * Test the exclude filtering of test methods.
     */
    public void testExcludeFilter() throws DeviceNotAvailableException {
        String excludeFilter1 = "*don?tRunMe*";
        String excludeFilter2 = "*orMe?*";
        mGTest.addExcludeFilter(excludeFilter1);
        mGTest.addExcludeFilter(excludeFilter2);

        doTestFilter(String.format(
                "%s=-%s:%s", GTEST_FLAG_FILTER, excludeFilter1, excludeFilter2));
    }

    /**
     * Test simultaneous include and exclude filtering of test methods.
     */
    public void testIncludeAndExcludeFilters() throws DeviceNotAvailableException {
        String includeFilter1 = "pleaseRunMe";
        String includeFilter2 = "andMe";
        String excludeFilter1 = "dontRunMe";
        String excludeFilter2 = "orMe";
        mGTest.addIncludeFilter(includeFilter1);
        mGTest.addExcludeFilter(excludeFilter1);
        mGTest.addIncludeFilter(includeFilter2);
        mGTest.addExcludeFilter(excludeFilter2);

        doTestFilter(String.format("%s=%s:%s-%s:%s", GTEST_FLAG_FILTER,
              includeFilter1, includeFilter2, excludeFilter1, excludeFilter2));
    }

    /**
     * Empty file exclusion regex filter should not skip any files
     */
    public void testFileExclusionRegexFilter_emptyfilters() {
        assertFalse(mGTest.shouldSkipFile("test_file"));
    }

    /**
     * File exclusion regex filter should skip invalid filepath.
     */
    public void testFileExclusionRegexFilter_invalidInputString() {
        assertTrue(mGTest.shouldSkipFile(null));
        assertTrue(mGTest.shouldSkipFile(""));
    }

    /**
     * File exclusion regex filter should skip matched filepaths.
     */
    public void testFileExclusionRegexFilter_skipMatched() {
        // Skip files ending in .txt
        mGTest.addFileExclusionFilterRegex(".*\\.txt");
        assertFalse(mGTest.shouldSkipFile("/some/path/file/binary"));
        assertFalse(mGTest.shouldSkipFile("/some/path/file/random.dat"));
        assertTrue(mGTest.shouldSkipFile("/some/path/file/test.txt"));
    }

    /**
     * File exclusion regex filter for multi filters.
     */
    public void testFileExclusionRegexFilter_skipMultiMatched() {
        // Skip files ending in .txt
        mGTest.addFileExclusionFilterRegex(".*\\.txt");
        // Also skip files ending in .dat
        mGTest.addFileExclusionFilterRegex(".*\\.dat");
        assertFalse(mGTest.shouldSkipFile("/some/path/file/binary"));
        assertTrue(mGTest.shouldSkipFile("/some/path/file/random.dat"));
        assertTrue(mGTest.shouldSkipFile("/some/path/file/test.txt"));
    }
}
