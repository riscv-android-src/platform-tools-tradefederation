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
import com.android.tradefed.result.ITestInvocationListener;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.TimeUnit;

/** Unit tests for {@link RustBinaryTest}. */
@RunWith(JUnit4.class)
public class RustBinaryTestTest {
    private ITestInvocationListener mMockInvocationListener = null;
    private IShellOutputReceiver mMockReceiver = null;
    private ITestDevice mMockITestDevice = null;
    private RustBinaryTest mRustBinaryTest;
    private OptionSetter mSetter;

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
        mSetter = new OptionSetter(mRustBinaryTest);
    }

    /** Helper that replays all mocks. */
    private void replayMocks() {
        EasyMock.replay(mMockInvocationListener, mMockITestDevice, mMockReceiver);
    }

    /** Helper that verifies all mocks. */
    private void verifyMocks() {
        EasyMock.verify(mMockInvocationListener, mMockITestDevice, mMockReceiver);
    }

    /** Test run when the test dir is not found on the device. */
    @Test
    public void testRun_noTestDir() throws DeviceNotAvailableException {
        EasyMock.expect(mMockITestDevice.doesFileExist(RustBinaryTest.DEFAULT_TEST_PATH))
                .andReturn(false);
        replayMocks();
        mRustBinaryTest.run(mMockInvocationListener);
        verifyMocks();
    }

    /** Test run when no device is set should throw an exception. */
    @Test
    public void testRun_noDevice() throws DeviceNotAvailableException {
        mRustBinaryTest.setDevice(null);
        replayMocks();
        try {
            mRustBinaryTest.run(mMockInvocationListener);
            fail("an exception should have been thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
        verifyMocks();
    }

    /** Test the run method for a couple tests */
    @Test
    public void testRun() throws DeviceNotAvailableException { // FAILED
        final String testPath = RustBinaryTest.DEFAULT_TEST_PATH;
        final String test1 = "test1";
        final String test2 = "test2";
        final String testPath1 = String.format("%s/%s", testPath, test1);
        final String testPath2 = String.format("%s/%s", testPath, test2);

        MockFileUtil.setMockDirContents(mMockITestDevice, testPath, test1, test2);
        EasyMock.expect(mMockITestDevice.doesFileExist(testPath)).andReturn(true);
        EasyMock.expect(mMockITestDevice.isDirectory(testPath)).andReturn(true);
        EasyMock.expect(mMockITestDevice.isDirectory(testPath1)).andReturn(false);
        // report the file as executable
        EasyMock.expect(mMockITestDevice.isExecutable(testPath1)).andReturn(true);
        EasyMock.expect(mMockITestDevice.isDirectory(testPath2)).andReturn(false);
        // report the file as executable
        EasyMock.expect(mMockITestDevice.isExecutable(testPath2)).andReturn(true);

        String[] files = new String[] {"test1", "test2"};
        EasyMock.expect(mMockITestDevice.getChildren(testPath)).andReturn(files);
        mMockITestDevice.executeShellCommand(
                EasyMock.contains(test1),
                EasyMock.same(mMockReceiver),
                EasyMock.anyLong(),
                (TimeUnit) EasyMock.anyObject(),
                EasyMock.anyInt());
        mMockITestDevice.executeShellCommand(
                EasyMock.contains(test2),
                EasyMock.same(mMockReceiver),
                EasyMock.anyLong(),
                (TimeUnit) EasyMock.anyObject(),
                EasyMock.anyInt());

        replayMocks();

        mRustBinaryTest.run(mMockInvocationListener);
        verifyMocks();
    }

    /** Test the run method when module name is specified */
    @Test
    public void testRun_moduleName() throws DeviceNotAvailableException { // FAILED
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
        mMockITestDevice.executeShellCommand(
                EasyMock.contains(modulePath),
                EasyMock.same(mMockReceiver),
                EasyMock.anyLong(),
                (TimeUnit) EasyMock.anyObject(),
                EasyMock.anyInt());
        // report the file as executable
        EasyMock.expect(mMockITestDevice.isExecutable(modulePath)).andReturn(true);

        replayMocks();

        mRustBinaryTest.run(mMockInvocationListener);
        verifyMocks();
    }

    /** Test the run method for a test in a subdirectory */
    @Test
    public void testRun_nested() throws DeviceNotAvailableException { // FAILED
        final String testPath = RustBinaryTest.DEFAULT_TEST_PATH;
        final String subFolderName = "subFolder";
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
        EasyMock.expect(mMockITestDevice.isDirectory(testPath + "/" + subFolderName))
                .andReturn(true);
        EasyMock.expect(mMockITestDevice.isDirectory(test1Path)).andReturn(false);
        // report the file as executable
        EasyMock.expect(mMockITestDevice.isExecutable(test1Path)).andReturn(true);
        String[] files = new String[] {subFolderName};
        EasyMock.expect(mMockITestDevice.getChildren(testPath)).andReturn(files);
        String[] files2 = new String[] {"test1"};
        EasyMock.expect(mMockITestDevice.getChildren(testPath + "/" + subFolderName))
                .andReturn(files2);
        mMockITestDevice.executeShellCommand(
                EasyMock.contains(test1Path),
                EasyMock.same(mMockReceiver),
                EasyMock.anyLong(),
                (TimeUnit) EasyMock.anyObject(),
                EasyMock.anyInt());

        replayMocks();

        mRustBinaryTest.run(mMockInvocationListener);
        verifyMocks();
    }
}
