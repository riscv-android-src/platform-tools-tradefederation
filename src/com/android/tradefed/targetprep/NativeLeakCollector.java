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
import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.ITestLoggerReceiver;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.StreamUtil;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.List;

/**
 * A {@link ITargetCleaner} that runs 'dumpsys meminfo --unreachable -a' to identify the unreachable
 * native memory currently held by each process.
 * <p>
 * Note: this preparer requires N platform or newer.
 */
@OptionClass(alias = "native-leak-collector")
public class NativeLeakCollector implements ITestLoggerReceiver, ITargetCleaner {
    private static final String UNREACHABLE_MEMINFO_CMD = "dumpsys -t %d meminfo --unreachable -a";
    private static final String DIRECT_UNREACHABLE_CMD = "dumpsys -t %d %s --unreachable";

    private ITestLogger mTestLogger;

    @Option(name = "disable", description = "If this preparer should be disabled.")
    private boolean mDisable = false;

    @Option(name = "dump-timeout", description = "Timeout limit for dumping unreachable native "
            + "memory allocation information. Can be in any valid duration format, e.g. 5m, 1h.",
            isTimeVal = true)
    private long mDumpTimeout = 5 * 60 * 1000; // defaults to 5m

    @Option(name = "log-filename", description = "The filename to give this log.")
    private String mLogFilename = "unreachable-meminfo";

    @Option(name = "additional-proc", description = "A list indicating any additional names to "
            + "query for unreachable native memory.")
    private List<String> mAdditionalProc = new ArrayList<String>();

    @Option(name = "additional-dump-timeout", description = "An additional timeout limit for any "
            + "direct unreachable memory dump commands specified by the 'additional-procs' option. "
            + "Can be in any valid duration format, e.g. 5m, 1h.",
            isTimeVal = true)
    private long mAdditionalDumpTimeout = 1 * 60 * 1000; // defaults to 1m

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

        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        device.executeShellCommand(String.format(UNREACHABLE_MEMINFO_CMD,
                mDumpTimeout / 1000), receiver, mDumpTimeout, TimeUnit.MILLISECONDS, 1);

        for (String proc : mAdditionalProc) {
            CLog.v("Querying for process, %s", proc);
            device.executeShellCommand(String.format(DIRECT_UNREACHABLE_CMD,
                    mAdditionalDumpTimeout / 1000, proc), receiver, mAdditionalDumpTimeout,
                    TimeUnit.MILLISECONDS, 1);
        }

        if (receiver.getOutput() != null && !receiver.getOutput().isEmpty()) {
            if (mTestLogger != null) {
                ByteArrayInputStreamSource byteOutput =
                        new ByteArrayInputStreamSource(receiver.getOutput().getBytes());
                mTestLogger.testLog(mLogFilename, LogDataType.TEXT, byteOutput);
                StreamUtil.cancel(byteOutput);
            } else {
                CLog.w("No test logger available, printing output here:\n%s", receiver.getOutput());
            }
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
