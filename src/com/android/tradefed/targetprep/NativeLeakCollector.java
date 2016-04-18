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
package com.android.tradefed.targetprep;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.ITestLoggerReceiver;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.util.StreamUtil;

/**
 * A {@link ITargetCleaner} that runs 'dumpsys meminfo --unreachable -a' to identify the unreachable
 * native memory currently held by each process.
 * <p>
 * Note: this preparer requires N platform or newer.
 */
@OptionClass(alias = "native-leak-collector")
public class NativeLeakCollector implements ITestLoggerReceiver, ITargetCleaner {
    private static final String UNREACHABLE_MEMINFO_CMD =
            "dumpsys -t %d meminfo --unreachable -a";

    private ITestLogger mTestLogger;

    @Option(name = "disable", description = "If this preparer should be disabled.")
    private boolean mDisable = false;

    @Option(name = "dump-timeout", description = "Timeout limit in for dumping unreachable native "
            + "memory allocation information. Can be in any valid duration format, e.g. 5m, 1h",
            isTimeVal = true)
    private long mDumpTimeout = 5 * 60 * 1000; // defaults to 5m

    @Option(name = "log-filename", description = "The filename to give this log.")
    private String mLogFilename = "unreachable-meminfo";

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        // No-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable e)
            throws DeviceNotAvailableException {
        if (mDisable || (e instanceof DeviceNotAvailableException)) {
            return;
        }

        String output = device.executeShellCommand(String.format(
                UNREACHABLE_MEMINFO_CMD, mDumpTimeout / 1000));
        if (output != null && !output.isEmpty()) {
            ByteArrayInputStreamSource byteOutput =
                    new ByteArrayInputStreamSource(output.getBytes());
            mTestLogger.testLog(mLogFilename, LogDataType.TEXT, byteOutput);
            StreamUtil.cancel(byteOutput);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTestLogger(ITestLogger testLogger) {
        mTestLogger = testLogger;
    }
}
