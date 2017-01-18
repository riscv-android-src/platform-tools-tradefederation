/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.NullDevice;
import com.android.tradefed.util.QuotationAwareTokenizer;

import java.util.Arrays;
import java.util.Map;

/**
 * A {@link IRemoteTest} for running tests against a separate TF installation.
 * <p/>
 * Launches an external java process to run the tests. Used for running the TF unit or
 * functional tests continuously.
 */
public class VersionedTfLauncher extends SubprocessTfLauncher implements IMultiDeviceTest {

    @Option(name = "tf-command-line", description = "The string of original command line "
            + "arguments.")
    private String mTfCommandline = null;

    private Map<ITestDevice, IBuildInfo> mDeviceInfos = null;

    /**
     * {@inheritDoc}
     * <p/>
     * The method tokenizes the command line arguments specified by --tf-command-line, and
     * appends the arguments to the subprocess of TF run. It also passes in the serial of the test
     * device through --serial option, to force the subprocess to use the device selected by the
     * parent TF process.
     */
    @Override
    protected void preRun() {
        super.preRun();

        if (mTfCommandline != null) {
            mCmdArgs.addAll(Arrays.asList(QuotationAwareTokenizer.tokenizeLine(mTfCommandline)));
        }

        // TODO: support multiple device test.
        if (mDeviceInfos == null || mDeviceInfos.size() == 0) {
            throw new RuntimeException("Device is not allocated for the test.");
        } else if (mDeviceInfos.size() > 1) {
            throw new RuntimeException("More than one devices are allocated for the test.");
        } else {
            ITestDevice device = mDeviceInfos.entrySet().iterator().next().getKey();
            if (device.getIDevice() instanceof NullDevice) {
                mCmdArgs.add("--null-device");
            } else {
                String serial = device.getSerialNumber();
                mCmdArgs.add("--serial");
                mCmdArgs.add(serial);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDeviceInfos(Map<ITestDevice, IBuildInfo> deviceInfos) {
        mDeviceInfos = deviceInfos;
    }
}
