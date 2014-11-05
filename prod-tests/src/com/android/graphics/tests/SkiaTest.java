/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.graphics.tests;

import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IFileEntry;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.testrunner.TestIdentifier;

import java.io.File;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 *  Test for running Skia native tests.
 *
 *  The test is not necessarily Skia specific, but it provides
 *  functionality that allows native Skia tests to be run.
 *
 *  Includes options to specify the Skia test app to run (inside
 *  nativetest directory), flags to pass to the test app, and a file
 *  to retrieve off the device after the test completes. (Skia test
 *  apps record their results to a json file, so retrieving this file
 *  allows us to view the results so long as the app completed.)
 */
@OptionClass(alias = "skia_native_tests")
public class SkiaTest implements IRemoteTest, IDeviceTest {
    private ITestDevice mDevice;

    static final String DEFAULT_NATIVETEST_PATH = "/data/nativetest";

    @Option(name = "native-test-device-path",
      description = "The path on the device where native tests are located.")
    private String mNativeTestDevicePath = DEFAULT_NATIVETEST_PATH;

    @Option(name = "skia-flags",
        description = "Flags to pass to the skia program.")
    private String mFlags = "";

    @Option(name = "skia-app",
        description = "Skia program to run.",
        mandatory = true)
    private String mSkiaApp = "";

    @Option(name = "skia-json",
        description = "Full path on device for json output file.")
    private String mOutputPath = "";

    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        if (mDevice == null) {
            throw new IllegalArgumentException("Device has not been set");
        }

        // Native Skia tests are in nativeTestDirectory/mSkiaApp/mSkiaApp.
        String fullPath = mNativeTestDevicePath + "/"
                + mSkiaApp + "/" + mSkiaApp;
        IFileEntry app = mDevice.getFileEntry(fullPath);
        if (app == null) {
            CLog.w("Could not find test %s in %s!", fullPath, mDevice.getSerialNumber());
            return;
        }

        runTest(app, mDevice, listener);
    }

    /**
     *  Emulates running mkdirs on an ITestDevice.
     *
     *  Creates the directory named by dir *on device*, recursively creating missing parent
     *  directories if necessary.
     *
     *  @param dir Directory to create.
     *  @param testDevice Device to create directories on.
     */
    private void mkdirs(File dir, ITestDevice testDevice) throws DeviceNotAvailableException {
        if (dir == null || testDevice.doesFileExist(dir.getPath())) {
            return;
        }
        // First, make sure that the parent folder exists.
        mkdirs(dir.getParentFile(), testDevice);

        String dirName = dir.getPath();
        CLog.v("creating folder '%s'", dirName);
        testDevice.executeShellCommand("mkdir " + dirName);
    }

    /**
     *  Run a test on a device.
     *
     *  @param app Test app to run.
     *  @param testDevice Device on which to run the test.
     *  @param listener Listener for reporting results.
     */
    private void runTest(IFileEntry app, ITestDevice testDevice,
            ITestInvocationListener listener) throws DeviceNotAvailableException {
        File outputFile = null;
        if (!mOutputPath.isEmpty()) {
            outputFile = new File(mOutputPath);
            String path = outputFile.getPath();
            if (testDevice.doesFileExist(path)) {
                // Delete the file. We don't want to think this file from an
                // earlier run represents this one.
                CLog.v("Removing old file " + path);
                testDevice.executeShellCommand("rm " + path);
            } else {
                mkdirs(outputFile.getParentFile(), testDevice);
            }
        }

        listener.testRunStarted(app.getName(), 1);

        String fullPath = app.getFullEscapedPath();
        // force file to be executable
        testDevice.executeShellCommand(String.format("chmod 755 %s",
                fullPath));

        // The device will not immediately capture logs. Delay running to
        // ensure capturing them. The amount to delay corresponds to
        // mLogStartDelay in TestDevice.java.
        testDevice.startLogcat();
        RunUtil.getDefault().sleep(5 * 1000);

        String cmd = fullPath + " " + mFlags;
        CLog.v("Running '%s' on %s", cmd, mDevice.getSerialNumber());

        // A Receiver is required to use the version of executeShellCommand
        // that specifies the timeout.
        DummyReceiver receiver = new DummyReceiver();
        // Use 10 minutes as the time allowed. FIXME: This is overkill, but some
        // tests take a really long time without outputting anything.
        testDevice.executeShellCommand(cmd, receiver, 10, TimeUnit.MINUTES, 1);

        if (outputFile != null) {
            String path = outputFile.getPath();
            CLog.v("adb pull %s (using pullFile)", path);
            File result = testDevice.pullFile(path);

            TestIdentifier testId = new TestIdentifier(app.getName(), "outputJson");
            listener.testStarted(testId);
            if (result == null) {
                listener.testFailed(testId, "Failed to create "
                        + outputFile.getName() + ". Check logcat for details.");
            } else {
                listener.testEnded(testId, null);
                CLog.v("pulled result file to " + result.getPath());
                FileInputStreamSource source = new FileInputStreamSource(result);
                listener.testLog(result.getName(), LogDataType.TEXT, source);
                source.cancel();
                if (!result.delete()) {
                    CLog.w("Failed to delete temporary file %s", result.getPath());
                }
            }
        }

        // Don't report a meaningful time.
        listener.testRunEnded(0, Collections.<String, String>emptyMap());
    }

    // This receiver just avoids an NPE when calling executeShellCommand.
    private class DummyReceiver implements IShellOutputReceiver {
        @Override
        public void addOutput(byte[] data, int offset, int length) {}

        @Override
        public void flush() {}

        @Override
        public boolean isCancelled() {
            return false;
        }
    }
}
