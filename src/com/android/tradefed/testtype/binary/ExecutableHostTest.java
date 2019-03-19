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
package com.android.tradefed.testtype.binary;

import com.android.annotations.VisibleForTesting;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.StubDevice;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Test runner for executable running on the host. The runner implements {@link IDeviceTest} since
 * the host binary might communicate to a device. If the received device is not a {@link StubDevice}
 * the serial will be passed to the binary to be used.
 */
public class ExecutableHostTest extends ExecutableBaseTest implements IDeviceTest {

    private static final String ANDROID_SERIAL = "ANDROID_SERIAL";

    @Option(
        name = "per-binary-timeout",
        isTimeVal = true,
        description = "Timeout applied to each binary for their execution."
    )
    private long mTimeoutPerBinaryMs = 5 * 60 * 1000L;

    private ITestDevice mDevice;

    @Override
    public boolean checkBinaryExists(String binaryPath) {
        return new File(binaryPath).exists();
    }

    @Override
    public void runBinary(String binaryPath, ITestInvocationListener listener) {
        IRunUtil runUtil = createRunUtil();
        // Output everything in stdout
        runUtil.setRedirectStderrToStdout(true);
        // If we are running against a real device, set ANDROID_SERIAL to the proper serial.
        if (!(mDevice.getIDevice() instanceof StubDevice)) {
            runUtil.setEnvVariable(ANDROID_SERIAL, mDevice.getSerialNumber());
        }
        // Ensure its executable
        FileUtil.chmodRWXRecursively(new File(binaryPath));

        List<String> command = new ArrayList<>();
        command.add(binaryPath);
        CommandResult res =
                runUtil.runTimedCmd(mTimeoutPerBinaryMs, command.toArray(new String[0]));
        if (!CommandStatus.SUCCESS.equals(res.getStatus())) {
            // Everything should be outputted in stdout with our redirect above.
            String errorMessage = res.getStdout();
            if (CommandStatus.TIMED_OUT.equals(res.getStatus())) {
                errorMessage += "\nTimeout.";
            }
            if (res.getExitCode() != null) {
                errorMessage += String.format("\nExit Code: %s", res.getExitCode());
            }
            listener.testRunFailed(errorMessage);
        }
    }

    @Override
    public final void setDevice(ITestDevice device) {
        mDevice = device;
    }

    @Override
    public final ITestDevice getDevice() {
        return mDevice;
    }

    @VisibleForTesting
    IRunUtil createRunUtil() {
        return new RunUtil();
    }
}
