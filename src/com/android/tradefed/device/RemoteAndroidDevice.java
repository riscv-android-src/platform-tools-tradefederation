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
package com.android.tradefed.device;

import com.android.ddmlib.IDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

/**
 * Implementation of a {@link ITestDevice} for a full stack android device connected via
 * adb connect.
 * Assume the device serial will be in the format <hostname>:<portnumber> in adb.
 */
public class RemoteAndroidDevice extends TestDevice {

    protected static final long RETRY_INTERVAL_MS = 5000;
    protected static final int MAX_RETRIES = 5;
    protected static final long DEFAULT_SHORT_CMD_TIMEOUT = 20 * 1000;

    private static final String ADB_SUCCESS_CONNECT_TAG = "connected to";
    private static final String ADB_ALREADY_CONNECTED_TAG = "already";

    /**
     * Creates a {@link RemoteAndroidDevice}.
     *
     * @param device the associated {@link IDevice}
     * @param stateMonitor the {@link IDeviceStateMonitor} mechanism to use
     * @param allocationMonitor the {@link IDeviceMonitor} to inform of allocation state changes.
     */
    public RemoteAndroidDevice(IDevice device, IDeviceStateMonitor stateMonitor,
            IDeviceMonitor allocationMonitor) {
        super(device, stateMonitor, allocationMonitor);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void postAdbRootAction() {
        // attempt to reconnect first to make sure we didn't loose the connection because of
        // adb root.
        adbTcpConnect(getHostName(), getPortNum());
    }

    /**
     * Return the hostname associated with the device. Extracted from the serial.
     */
    public String getHostName() {
        if (!checkSerialFormatValid()) {
            throw new RuntimeException(
                    String.format("Serial Format is unexpected: %s "
                            + "should look like <hostname>:<port>", getSerialNumber()));
        }
        return getSerialNumber().split(":")[0];
    }

    /**
     * Return the port number asociated with the device. Extracted from the serial.
     */
    public String getPortNum() {
        if (!checkSerialFormatValid()) {
            throw new RuntimeException(
                    String.format("Serial Format is unexpected: %s "
                            + "should look like <hostname>:<port>", getSerialNumber()));
        }
        return getSerialNumber().split(":")[1];
    }

    /**
     * Check if the format of the serial is as expected <hostname>:port
     * @return true if the format is valid, false otherwise.
     */
    private boolean checkSerialFormatValid() {
        String[] serial =  getSerialNumber().split(":");
        if (serial.length == 2) {
            try {
                Integer.parseInt(serial[1]);
                return true;
            } catch (NumberFormatException nfe) {
                return false;
            }
        }
        return false;
    }

    /**
     * Helper method to adb connect to a given tcp ip Android device
     *
     * @param host the hostname/ip of a tcp/ip Android device
     * @param port the port number of a tcp/ip device
     * @return true if we successfully connected to the device, false
     *         otherwise.
     */
    public boolean adbTcpConnect(String host, String port) {
        for (int i = 0; i < MAX_RETRIES; i++) {
            CommandResult result = getRunUtil().runTimedCmd(DEFAULT_SHORT_CMD_TIMEOUT, "adb",
                    "connect", String.format("%s:%s", host, port));
            if (CommandStatus.SUCCESS.equals(result.getStatus()) &&
                result.getStdout().contains(ADB_SUCCESS_CONNECT_TAG)) {
                CLog.d("adb connect output: status: %s stdout: %s stderr: %s",
                        result.getStatus(), result.getStdout(), result.getStderr());

                // It is possible to get a positive result without it being connected because of
                // the ssh bridge. Retrying to get confirmation, and expecting "already connected".
                CommandResult resultConfirmation =
                        getRunUtil().runTimedCmd(DEFAULT_SHORT_CMD_TIMEOUT, "adb", "connect",
                        String.format("%s:%s", host, port));
                if (CommandStatus.SUCCESS.equals(resultConfirmation.getStatus()) &&
                        resultConfirmation.getStdout().contains(ADB_ALREADY_CONNECTED_TAG)) {
                    CLog.d("adb connect confirmed:\nstdout: %s\nsterr: %s",
                            resultConfirmation.getStdout(), resultConfirmation.getStderr());
                    return true;
                }
                else {
                    CLog.d("adb connect confirmation failed:\nstatus:%s\nstdout: %s\nsterr: %s",
                            resultConfirmation.getStatus(), resultConfirmation.getStdout(),
                            resultConfirmation.getStderr());
                }
            }
            CLog.d("adb connect output: status: %s stdout: %s stderr: %s, retrying.",
                    result.getStatus(), result.getStdout(), result.getStderr());
            getRunUtil().sleep((i + 1) * RETRY_INTERVAL_MS);
        }
        return false;
    }
}
