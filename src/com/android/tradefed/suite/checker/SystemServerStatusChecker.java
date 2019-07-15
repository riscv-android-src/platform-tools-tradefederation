/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tradefed.suite.checker;

import com.android.annotations.VisibleForTesting;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.suite.checker.StatusCheckerResult.CheckStatus;
import com.android.tradefed.util.ProcessInfo;

import java.util.Map;

/**
 * Check if the pid of system_server has changed from before and after a module run. A new pid would
 * mean a runtime restart occurred during the module run.
 */
public class SystemServerStatusChecker implements ISystemStatusChecker {

    private ProcessInfo mSystemServerProcess;
    private Long mModuleStartTime = null;

    /** {@inheritDoc} */
    @Override
    public StatusCheckerResult preExecutionCheck(ITestDevice device)
            throws DeviceNotAvailableException {
        mSystemServerProcess = device.getProcessByName("system_server");
        StatusCheckerResult result = new StatusCheckerResult(CheckStatus.SUCCESS);
        if (mSystemServerProcess == null) {
            String message = "No valid system_server process is found.";
            CLog.w(message);
            result.setStatus(CheckStatus.FAILED);
            result.setBugreportNeeded(true);
            result.setErrorMessage(message);
            mModuleStartTime = null;
            return result;
        }
        mModuleStartTime = getCurrentTime();
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public StatusCheckerResult postExecutionCheck(ITestDevice device)
            throws DeviceNotAvailableException {
        if (mSystemServerProcess == null) {
            CLog.d(
                    "No valid system_server process was found in preExecutionCheck, "
                            + "skipping system_server postExecutionCheck.");
            return new StatusCheckerResult(CheckStatus.SUCCESS);
        }
        String message = null;
        ProcessInfo currSystemServerProcess = device.getProcessByName("system_server");
        if (currSystemServerProcess == null) {
            message = "system_server is down";
            CLog.w(message);
            StatusCheckerResult result = new StatusCheckerResult(CheckStatus.FAILED);
            result.setBugreportNeeded(true);
            result.setErrorMessage(message);
            return result;
        }

        if (currSystemServerProcess.getPid() == mSystemServerProcess.getPid()
                && currSystemServerProcess.getStartTime() == mSystemServerProcess.getStartTime()) {
            return new StatusCheckerResult(CheckStatus.SUCCESS);
        }
        //system_server restarted
        Map<Long, String> bootHistory =
                device.getBootHistorySince(mSystemServerProcess.getStartTime());
        CLog.i("The device reboot with boot history: %s", bootHistory);
        if (bootHistory.isEmpty()) {
            message = "system_server restarted without device reboot";
        } else {
            message = "system_server restarted with device boot history: " + bootHistory.toString();
            // Check if there is a TF triggered reboot with device.doReboot
            long lastExpectedReboot = device.getLastExpectedRebootTimeMillis();
            if (mModuleStartTime != null && lastExpectedReboot < mModuleStartTime) {
                // The reboot is not triggered by Tradefed host.
                CLog.w(
                        "System_server restarted and Tradefed didn't trigger a reboot: "
                                + "last expected reboot: %s, module start time: %s, "
                                + "something went wrong.",
                        lastExpectedReboot, mModuleStartTime);
            } else {
                // The reboot is triggered by Tradefed host
                CLog.i("Tradefed triggered reboot detected");
                return new StatusCheckerResult(CheckStatus.SUCCESS);
            }
        }
        CLog.w(message);
        StatusCheckerResult result = new StatusCheckerResult(CheckStatus.FAILED);
        result.setBugreportNeeded(true);
        result.setErrorMessage(message);
        return result;
    }

    /** Returns the current time. */
    @VisibleForTesting
    protected long getCurrentTime() {
        return System.currentTimeMillis();
    }

}
