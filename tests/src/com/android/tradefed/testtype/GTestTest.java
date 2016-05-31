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
import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.MockFileUtil;
import com.android.tradefed.result.ITestInvocationListener;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.io.File;
import java.util.concurrent.TimeUnit;


/**
 * Unit tests for {@link GTestTest}.
 */
public class GTestTest extends TestCase {
    private static final String GTEST_FLAG_FILTER = "--gtest_filter";
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
        mMockReceiver.flush();
        EasyMock.expectLastCall().anyTimes();
        EasyMock.expect(mMockITestDevice.getSerialNumber()).andStubReturn("serial");
        mGTest = new GTest() {
            @Override
            IShellOutputReceiver createResultParser(String runName, ITestRunListener listener) {
                return mMockReceiver;
            }
            @Override
            GTestXmlResultParser createXmlParser(String testRunName, ITestRunListener listener) {
                return new GTestXmlResultParser(testRunName, listener) {
                    @Override
                    public void parseResult(File f, CollectingOutputReceiver output) {
                        return;
                    }
                };
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
        EasyMock.expect(mMockITestDevice.isDirectory(nativeTestPath)).andReturn(true);
        EasyMock.expect(mMockITestDevice.isDirectory(nativeTestPath + "/test1")).andReturn(false);
        EasyMock.expect(mMockITestDevice.isDirectory(nativeTestPath + "/test2")).andReturn(false);
        String[] files = new String[] {"test1", "test2"};
        EasyMock.expect(mMockITestDevice.getChildren(nativeTestPath)).andReturn(files);
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
        EasyMock.expect(mMockITestDevice.isDirectory(modulePath)).andReturn(false);
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
        EasyMock.expect(mMockITestDevice.isDirectory(nativeTestPath)).andReturn(true);
        EasyMock.expect(mMockITestDevice.isDirectory(nativeTestPath + "/" + subFolderName))
                .andReturn(true);
        EasyMock.expect(mMockITestDevice.isDirectory(test1Path)).andReturn(false);
        String[] files = new String[] {subFolderName};
        EasyMock.expect(mMockITestDevice.getChildren(nativeTestPath)).andReturn(files);
        String[] files2 = new String[] {"test1"};
        EasyMock.expect(mMockITestDevice.getChildren(nativeTestPath + "/" + subFolderName))
                .andReturn(files2);
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
        EasyMock.expect(mMockITestDevice.isDirectory(nativeTestPath)).andReturn(true);
        EasyMock.expect(mMockITestDevice.isDirectory(nativeTestPath + "/test1")).andReturn(false);
        String[] files = new String[] {"test1"};
        EasyMock.expect(mMockITestDevice.getChildren(nativeTestPath)).andReturn(files);
        EasyMock.expect(mMockITestDevice.executeShellCommand(EasyMock.contains("chmod")))
                .andReturn("");
        mMockITestDevice.executeShellCommand(EasyMock.contains(filterString),
                EasyMock.same(mMockReceiver), EasyMock.anyLong(), (TimeUnit)EasyMock.anyObject(),
                EasyMock.anyInt());
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
        mGTest.addExcludeFilter(excludeFilter1);

        doTestFilter(String.format(
                "%s=-%s", GTEST_FLAG_FILTER, excludeFilter1));
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
     * Test behavior for command lines too long to be run by ADB
     */
    public void testCommandTooLong() throws DeviceNotAvailableException {
        String deviceScriptPath = "/data/local/tmp/gtest_script.sh";
        StringBuilder filterString = new StringBuilder(GTEST_FLAG_FILTER);
        filterString.append("=-");
        for (int i = 0; i < 100; i++) {
            if (i != 0) {
                filterString.append(":");
            }
            String filter = String.format("ExcludeClass%d", i);
            filterString.append(filter);
            mGTest.addExcludeFilter(filter);
        }
        // filter string will be longer than GTest.GTEST_CMD_CHAR_LIMIT

        String nativeTestPath = GTest.DEFAULT_NATIVETEST_PATH;
        // configure the mock file system to have a single test
        MockFileUtil.setMockDirContents(mMockITestDevice, nativeTestPath, "test1");
        EasyMock.expect(mMockITestDevice.doesFileExist(nativeTestPath)).andReturn(true);
        EasyMock.expect(mMockITestDevice.isDirectory(nativeTestPath)).andReturn(true);
        EasyMock.expect(mMockITestDevice.isDirectory(nativeTestPath + "/test1")).andReturn(false);
        String[] files = new String[] {"test1"};
        EasyMock.expect(mMockITestDevice.getChildren(nativeTestPath)).andReturn(files);
        // Expect push of script file
        EasyMock.expect(mMockITestDevice.pushString(EasyMock.<String>anyObject(),
                EasyMock.eq(deviceScriptPath))).andReturn(Boolean.TRUE);
        // chmod 755 for both the gtest executable and the shell script
        EasyMock.expect(mMockITestDevice.executeShellCommand(EasyMock.contains("chmod")))
                .andReturn("").times(2);
        // Expect command to run shell script, rather than direct adb command
        mMockITestDevice.executeShellCommand(EasyMock.eq(String.format("sh %s", deviceScriptPath)),
                EasyMock.same(mMockReceiver), EasyMock.anyLong(), (TimeUnit)EasyMock.anyObject(),
                EasyMock.anyInt());
        // Expect deletion of file on device
        EasyMock.expect(mMockITestDevice.executeShellCommand(
                EasyMock.eq(String.format("rm %s", deviceScriptPath)))).andReturn("");
        replayMocks();
        mGTest.run(mMockInvocationListener);

        verifyMocks();
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

    /**
     * Test the run method for a couple tests
     */
    public void testRunXml() throws DeviceNotAvailableException {
        mGTest.setEnableXmlOutput(true);

        final String nativeTestPath = GTest.DEFAULT_NATIVETEST_PATH;
        final String test1 = "test1";
        final String test2 = "test2";

        MockFileUtil.setMockDirContents(mMockITestDevice, nativeTestPath, test1, test2);
        EasyMock.expect(mMockITestDevice.doesFileExist(nativeTestPath)).andReturn(true);
        EasyMock.expect(mMockITestDevice.isDirectory(nativeTestPath)).andReturn(true);
        EasyMock.expect(mMockITestDevice.isDirectory(nativeTestPath + "/test1")).andReturn(false);
        EasyMock.expect(mMockITestDevice.isDirectory(nativeTestPath + "/test2")).andReturn(false);
        String[] files = new String[] {"test1", "test2"};
        EasyMock.expect(mMockITestDevice.getChildren(nativeTestPath)).andReturn(files);
        EasyMock.expect(mMockITestDevice.executeShellCommand(EasyMock.contains("chmod")))
                .andReturn("")
                .times(2);
        EasyMock.expect(mMockITestDevice.executeShellCommand(EasyMock.contains("rm")))
                .andReturn("")
                .times(2);
        EasyMock.expect(mMockITestDevice.pullFile((String)EasyMock.anyObject(),
                (File)EasyMock.anyObject())).andStubReturn(true);
        mMockITestDevice.executeShellCommand(EasyMock.contains(test1),
                (CollectingOutputReceiver) EasyMock.anyObject(),
                EasyMock.anyLong(), (TimeUnit)EasyMock.anyObject(), EasyMock.anyInt());
        mMockITestDevice.executeShellCommand(EasyMock.contains(test2),
                (CollectingOutputReceiver) EasyMock.anyObject(),
                EasyMock.anyLong(), (TimeUnit)EasyMock.anyObject(), EasyMock.anyInt());
        replayMocks();

        mGTest.run(mMockInvocationListener);
        verifyMocks();
    }

    public void testGetFileName() {
        String expected = "bar";
        String s1 = "/foo/" + expected;
        String s2 = expected;
        String s3 = "/foo/";
        assertEquals(expected, mGTest.getFileName(s1));
        assertEquals(expected, mGTest.getFileName(s2));
        try {
            mGTest.getFileName(s3);
            fail("Expected IllegalArgumentException not thrown");
        } catch (IllegalArgumentException iae) {
            // expected
        }
    }
}
