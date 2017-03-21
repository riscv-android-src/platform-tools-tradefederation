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

package com.android.tradefed.util;

import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility functions for calling BluetoothInstrumentation on device
 * <p>
 * Device side BluetoothInstrumentation code can be found in AOSP at:
 * <code>frameworks/base/core/tests/bluetoothtests</code>
 *
 */
public class BluetoothUtils {

    private static final String BT_INSTR_CMD = "am instrument -w -r -e command %s "
            + "com.android.bluetooth.tests/android.bluetooth.BluetoothInstrumentation";
    private static final String SUCCESS_INSTR_OUTPUT = "INSTRUMENTATION_RESULT: result=SUCCESS";
    private static final String BT_GETADDR_HEADER = "INSTRUMENTATION_RESULT: address=";
    private static final long BASE_RETRY_DELAY_MS = 60 * 1000;
    private static final int MAX_RETRIES = 3;
    private static final Pattern BONDED_MAC_HEADER =
            Pattern.compile("INSTRUMENTATION_RESULT: device-\\d{2}=(.*)$");
    private static final String BTSNOOP_LOG_FILE = "btsnoop_hci.log";
    private static final String BTSNOOP_CONF_FILE = "/etc/bluetooth/bt_stack.conf";

    /**
     * Convenience method to execute BT instrumentation command and return output
     *
     * @param device
     * @param command a command string sent over to BT instrumentation, currently supported:
     *                 enable, disable, unpairAll, getName, getAddress, getBondedDevices; refer to
     *                 AOSP source for more details
     * @return output of BluetoothInstrumentation
     * @throws DeviceNotAvailableException
     */
    public static String runBluetoothInstrumentation(ITestDevice device, String command)
            throws DeviceNotAvailableException {
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        device.executeShellCommand(String.format(BT_INSTR_CMD, command), receiver);
        String output = receiver.getOutput();
        CLog.v("bluetooth instrumentation sub command: %s\noutput:\n", command);
        CLog.v(output);
        return output;
    }

    public static boolean runBluetoothInstrumentationWithRetry(ITestDevice device, String command)
        throws DeviceNotAvailableException {
        for (int retry = 0; retry < MAX_RETRIES; retry++) {
            String output = runBluetoothInstrumentation(device, command);
            if (output.contains(SUCCESS_INSTR_OUTPUT)) {
                return true;
            }
            RunUtil.getDefault().sleep(retry * BASE_RETRY_DELAY_MS);
        }
        return false;
    }

    /**
     * Retries clearing of BT pairing with linear backoff
     * @param device
     * @throws DeviceNotAvailableException
     */
    public static boolean unpairWithRetry(ITestDevice device)
            throws DeviceNotAvailableException {
        return runBluetoothInstrumentationWithRetry(device, "unpairAll");
    }

    /**
     * Retrieves BT mac of the given device
     *
     * @param device
     * @return BT mac or null if not found
     * @throws DeviceNotAvailableException
     */
    public static String getBluetoothMac(ITestDevice device) throws DeviceNotAvailableException {
        String lines[] = runBluetoothInstrumentation(device, "getAddress").split("\\r?\\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith(BT_GETADDR_HEADER)) {
                return line.substring(BT_GETADDR_HEADER.length());
            }
        }
        return null;
    }

    /**
     * Enables bluetooth on the given device
     *
     * @param device
     * @return True if enable is successful, false otherwise
     * @throws DeviceNotAvailableException
     */
    public static boolean enable(ITestDevice device)
            throws DeviceNotAvailableException {
        return runBluetoothInstrumentationWithRetry(device, "enable");
    }

    /**
     * Disables bluetooth on the given device
     *
     * @param device
     * @return True if disable is successful, false otherwise
     * @throws DeviceNotAvailableException
     */
    public static boolean disable(ITestDevice device)
            throws DeviceNotAvailableException {
        return runBluetoothInstrumentationWithRetry(device, "disable");
    }

    /**
     * Returns bluetooth mac addresses of devices that the given device has bonded with
     *
     * @param device
     * @return bluetooth mac addresses
     * @throws DeviceNotAvailableException
     */
    public static Set<String> getBondedDevices(ITestDevice device)
            throws DeviceNotAvailableException {
        String lines[] = runBluetoothInstrumentation(device, "getBondedDevices").split("\\r?\\n");
        return parseBondedDeviceInstrumentationOutput(lines);
    }

    /** Parses instrumentation output into mac addresses */
    static Set<String> parseBondedDeviceInstrumentationOutput(String[] lines) {
        Set<String> ret = new HashSet<>();
        for (String line : lines) {
            Matcher m = BONDED_MAC_HEADER.matcher(line.trim());
            if (m.find()) {
                ret.add(m.group(1));
            }
        }
        return ret;
    }

    /**
     * Enable btsnoop logging by changing the BtSnoopLogOutput line in /etc/bluetooth/bt_stack.conf
     * to true.
     */
    public static boolean enableBtsnoopLogging(ITestDevice device)
            throws DeviceNotAvailableException {
        File confFile = device.pullFile(BTSNOOP_CONF_FILE);
        if (confFile == null) {
            return false;
        }

        BufferedReader confReader = null;
        try {
            confReader = new BufferedReader(new FileReader(confFile));
            StringBuilder newConf = new StringBuilder();
            String line;
            while ((line = confReader.readLine()) != null) {
                if (line.startsWith("BtSnoopLogOutput=")) {
                    newConf.append("BtSnoopLogOutput=true\n");
                } else {
                    newConf.append(line).append("\n");
                }
            }
            device.remountSystemWritable();
            CLog.d("System remount complete");
            return device.pushString(newConf.toString(), BTSNOOP_CONF_FILE);
        } catch (IOException e) {
            CLog.e(e);
            return false;
        } finally {
            // Delete host's copy of configuration file
            FileUtil.deleteFile(confFile);
            StreamUtil.close(confReader);
            device.reboot();
        }
    }

    /**
     * Disable btsnoop logging by changing the BtSnoopLogOutput line in /etc/bluetooth/bt_stack.conf
     * to false.
     */
    public static boolean disableBtsnoopLogging(ITestDevice device)
            throws DeviceNotAvailableException {
        File confFile = device.pullFile(BTSNOOP_CONF_FILE);
        if (confFile == null) {
            return false;
        }

        BufferedReader confReader = null;
        try {
            confReader = new BufferedReader(new FileReader(confFile));
            StringBuilder newConf = new StringBuilder();
            String line;
            while ((line = confReader.readLine()) != null) {
                if (line.startsWith("BtSnoopLogOutput=")) {
                    newConf.append("BtSnoopLogOutput=false\n");
                } else {
                    newConf.append(line).append("\n");
                }
            }
            device.remountSystemWritable();
            return device.pushString(newConf.toString(), BTSNOOP_CONF_FILE);
        } catch (IOException e) {
            return false;
        } finally {
            // Delete host's copy of configuration file
            FileUtil.deleteFile(confFile);
            StreamUtil.close(confReader);
            // Delete BT snoop log
            cleanLogFile(device);
        }
    }

    /**
     * Upload snoop log file for test results
     */
    public static void uploadLogFiles(ITestInvocationListener listener, ITestDevice device,
            String type, int iteration) throws DeviceNotAvailableException {
        File logFile = null;
        InputStreamSource logSource = null;
        try {
            logFile = device.pullFileFromExternal(BTSNOOP_LOG_FILE);
            if (logFile != null) {
                CLog.d("Sending %s %d byte file %s into the logosphere!", type, logFile.length(),
                        logFile);
                logSource = new FileInputStreamSource(logFile);
                listener.testLog(String.format("%s_btsnoop_%d", type, iteration),
                        LogDataType.UNKNOWN, logSource);
            }
        } finally {
            FileUtil.deleteFile(logFile);
            StreamUtil.cancel(logSource);
        }
    }

    /**
     * Delete snoop log file from device
     */
    public static void cleanLogFile(ITestDevice device) throws DeviceNotAvailableException {
        device.executeShellCommand(String.format("rm ${EXTERNAL_STORAGE}/%s", BTSNOOP_LOG_FILE));
    }
}
