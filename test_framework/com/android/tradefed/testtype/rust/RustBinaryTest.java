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

import com.android.ddmlib.FileListingService;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IDeviceTest;

import java.io.File;
import java.util.concurrent.TimeUnit;

/** A Test that runs a rust binary on given device. */
@OptionClass(alias = "rust-device")
public class RustBinaryTest extends RustTestBase implements IDeviceTest {

    static final String DEFAULT_TEST_PATH = "/data/local/tmp";

    // TODO(chh): add "ld-library-path" option and set up LD_LIBRARY_PATH

    @Option(
            name = "test-device-path",
            description = "The path on the device where tests are located.")
    private String mTestDevicePath = DEFAULT_TEST_PATH;

    @Option(name = "module-name", description = "The name of the test module to run.")
    private String mTestModule = null;

    private ITestDevice mDevice = null;

    /** {@inheritDoc} */
    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /** {@inheritDoc} */
    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    public void setModuleName(String name) {
        mTestModule = name;
    }

    public String getTestModule() {
        return mTestModule;
    }

    // TODO(chh): implement test filter

    /**
     * Gets the path where tests live on the device.
     *
     * @return The path on the device where the tests live.
     */
    private String getTestPath() {
        StringBuilder testPath = new StringBuilder(mTestDevicePath);
        String testModule = getTestModule();
        if (testModule != null) {
            testPath.append(FileListingService.FILE_SEPARATOR);
            testPath.append(testModule);
        }
        return testPath.toString();
    }

    // Returns true if given fullPath is not executable.
    private boolean shouldSkipFile(String fullPath) throws DeviceNotAvailableException {
        return fullPath == null || fullPath.isEmpty() || !mDevice.isExecutable(fullPath);
    }

    /**
     * Executes all tests in a folder as well as in all subfolders recursively.
     *
     * @param root The root folder to begin searching for tests
     * @param testDevice The device to run tests on
     * @param listener the {@link ITestInvocationListener}
     * @throws DeviceNotAvailableException
     */
    private void doRunAllTestsInSubdirectory(
            String root, ITestDevice testDevice, ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        if (testDevice.isDirectory(root)) {
            // recursively run tests in all subdirectories
            for (String child : testDevice.getChildren(root)) {
                doRunAllTestsInSubdirectory(root + "/" + child, testDevice, listener);
            }
        } else if (shouldSkipFile(root)) {
            CLog.d("Skip rust test %s on %s", root, testDevice.getSerialNumber());
        } else {
            runTest(testDevice, createParser(listener, new File(root).getName()), root);
        }
    }

    /**
     * Run the given Rust binary
     *
     * @param testDevice the {@link ITestDevice}
     * @param resultParser the test run output parser
     * @param fullPath absolute file system path to rust binary on device
     * @throws DeviceNotAvailableException
     */
    private void runTest(
            final ITestDevice testDevice,
            final IShellOutputReceiver resultParser,
            final String fullPath)
            throws DeviceNotAvailableException {
        // TODO(chh): add rerun support
        CLog.d("RustBinaryTest runTest: " + fullPath);
        String cmd = fullPath; // TODO(chh): add LD_LIBRARY_PATH
        testDevice.executeShellCommand(
                cmd, resultParser, mTestTimeout, TimeUnit.MILLISECONDS, 0 /* retryAttempts */);
    }

    /** {@inheritDoc} */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        if (mDevice == null) {
            throw new IllegalArgumentException("Device has not been set");
        }

        String testPath = getTestPath();
        if (!mDevice.doesFileExist(testPath)) {
            CLog.d(
                    "Could not find test directory %s in device %s!",
                    testPath, mDevice.getSerialNumber());
            return;
        }
        CLog.d(
                "Found and run test directory %s in device %s!",
                testPath, mDevice.getSerialNumber());

        doRunAllTestsInSubdirectory(testPath, mDevice, listener);
    }
}
