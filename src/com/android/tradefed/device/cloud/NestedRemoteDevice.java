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
package com.android.tradefed.device.cloud;

import com.android.ddmlib.IDevice;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IDeviceMonitor;
import com.android.tradefed.device.IDeviceStateMonitor;
import com.android.tradefed.device.TestDevice;
import com.android.tradefed.invoker.RemoteInvocationExecution;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import com.google.common.base.Joiner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Representation of the device running inside a remote Cuttlefish VM. It will alter the local
 * device {@link TestDevice} behavior in some cases to take advantage of the setup.
 */
public class NestedRemoteDevice extends TestDevice {

    // TODO: Improve the way we associate nested device with their user
    private static final Map<String, String> IP_TO_USER = new HashMap<>();

    static {
        IP_TO_USER.put("127.0.0.1:6520", "vsoc-01");
        IP_TO_USER.put("127.0.0.1:6521", "vsoc-02");
        IP_TO_USER.put("127.0.0.1:6522", "vsoc-03");
        IP_TO_USER.put("127.0.0.1:6523", "vsoc-04");
        IP_TO_USER.put("127.0.0.1:6524", "vsoc-05");
        IP_TO_USER.put("127.0.0.1:6525", "vsoc-06");
        IP_TO_USER.put("127.0.0.1:6526", "vsoc-07");
    }

    /**
     * Creates a {@link NestedRemoteDevice}.
     *
     * @param device the associated {@link IDevice}
     * @param stateMonitor the {@link IDeviceStateMonitor} mechanism to use
     * @param allocationMonitor the {@link IDeviceMonitor} to inform of allocation state changes.
     */
    public NestedRemoteDevice(
            IDevice device, IDeviceStateMonitor stateMonitor, IDeviceMonitor allocationMonitor) {
        super(device, stateMonitor, allocationMonitor);
        // TODO: Use IDevice directly
        if (stateMonitor instanceof NestedDeviceStateMonitor) {
            ((NestedDeviceStateMonitor) stateMonitor).setDevice(this);
        }
    }

    /**
     * Teardown and restore the virtual device so testing can proceed.
     *
     * <p>TODO: Restore device setup
     */
    public final boolean resetVirtualDevice(IBuildInfo info) throws DeviceNotAvailableException {
        String username = IP_TO_USER.get(getSerialNumber());
        // stop_cvd
        String stopCvdCommand = String.format("sudo runuser -l %s -c 'stop_cvd'", username);
        CommandResult stopCvdRes = getRunUtil().runTimedCmd(60000L, stopCvdCommand.split(" "));
        if (!CommandStatus.SUCCESS.equals(stopCvdRes.getStatus())) {
            CLog.e("%s", stopCvdRes.getStderr());
            // Log 'adb devices' to confirm device is gone
            CommandResult printAdbDevices = getRunUtil().runTimedCmd(60000L, "adb", "devices");
            CLog.e("%s\n%s", printAdbDevices.getStdout(), printAdbDevices.getStderr());
            // Proceed here, device could have been already gone.
        }
        // Synchronize this so multiple reset do not occur at the same time inside one VM.
        synchronized (NestedRemoteDevice.class) {
            // Own the image files
            String chownUserCommand = MultiUserSetupUtil.getChownCommand(username);
            CommandResult chownRes = getRunUtil().runTimedCmd(60000L, "sh", "-c", chownUserCommand);
            if (!CommandStatus.SUCCESS.equals(chownRes.getStatus())) {
                CLog.e("%s", chownRes.getStderr());
                return false;
            }
            // Restart the device
            List<String> createCommand = LaunchCvdHelper.createSimpleDeviceCommand(username, true);
            CommandResult createRes =
                    getRunUtil()
                            .runTimedCmd(
                                    RemoteInvocationExecution.LAUNCH_EXTRA_DEVICE,
                                    "sh",
                                    "-c",
                                    Joiner.on(" ").join(createCommand));
            if (!CommandStatus.SUCCESS.equals(createRes.getStatus())) {
                CLog.e("%s", createRes.getStderr());
                return false;
            }
            // Wait for the device to start for real.
            getRunUtil().sleep(5000);
            waitForDeviceAvailable();
            reInitDevice(info);
            return true;
        }
    }

    private void reInitDevice(IBuildInfo info) throws DeviceNotAvailableException {
        // Reset recovery since it's a new device
        setRecoveryMode(RecoveryMode.AVAILABLE);
        try {
            preInvocationSetup(info);
        } catch (TargetSetupError e) {
            throw new DeviceNotAvailableException(
                    "Failed re-init of device.", e, getSerialNumber());
        }
    }
}
