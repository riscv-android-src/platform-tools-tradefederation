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

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.tradefed.device.DeviceManager.FastbootDevice;
import com.android.tradefed.log.LogUtil.CLog;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Factory to create the different kind of devices that can be monitored by Tf
 */
public class ManagedTestDeviceFactory implements IManagedTestDeviceFactory {

    private static final String IPADDRESS_PATTERN =
            "((^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
            "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
            "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
            "([01]?\\d\\d?|2[0-4]\\d|25[0-5]))|(localhost)){1}";

    protected boolean mFastbootEnabled;
    protected IDeviceManager mDeviceManager;
    protected IDeviceMonitor mAllocationMonitor;
    protected static final String CHECK_PM_CMD = "test -e /system/bin/pm && echo %s";
    protected static final String EXPECTED_RES = "exists";

    public ManagedTestDeviceFactory(boolean fastbootEnabled, IDeviceManager deviceManager,
            IDeviceMonitor allocationMonitor) {
        mFastbootEnabled = fastbootEnabled;
        mDeviceManager = deviceManager;
        mAllocationMonitor = allocationMonitor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IManagedTestDevice createDevice(IDevice idevice) {
        IManagedTestDevice testDevice = null;
        if (idevice instanceof TcpDevice || isTcpDeviceSerial(idevice.getSerialNumber())) {
            // Special device for Tcp device for custom handling.
            testDevice = new RemoteAndroidDevice(idevice,
                    new DeviceStateMonitor(mDeviceManager, idevice, mFastbootEnabled),
                    mAllocationMonitor);
            testDevice.setDeviceState(TestDeviceState.NOT_AVAILABLE);
        } else if (!checkFrameworkSupport(idevice)) {
            // Brillo device instance tier 1 (no framework support)
            testDevice = new AndroidNativeDevice(idevice,
                    new AndroidNativeDeviceStateMonitor(mDeviceManager, idevice, mFastbootEnabled),
                    mAllocationMonitor);
        } else {
            // Default to-go device is Android full stack device.
            testDevice = new TestDevice(idevice,
                    new DeviceStateMonitor(mDeviceManager, idevice, mFastbootEnabled),
                    mAllocationMonitor);
        }

        if (idevice instanceof FastbootDevice) {
            testDevice.setDeviceState(TestDeviceState.FASTBOOT);
        } else if (idevice instanceof StubDevice) {
            testDevice.setDeviceState(TestDeviceState.NOT_AVAILABLE);
        }
        testDevice.setFastbootEnabled(mFastbootEnabled);
        return testDevice;
    }

    /**
     * Helper that return true if device has framework support.
     */
    private boolean checkFrameworkSupport(IDevice idevice) {
        if (idevice instanceof StubDevice) {
            // Assume stub device should go to the default full framework support for
            // backward compatibility
            return true;
        }
        final long timeout = 60 * 1000;
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        try {
            String cmd = String.format(CHECK_PM_CMD, EXPECTED_RES);
            idevice.executeShellCommand(cmd, receiver, timeout, TimeUnit.MILLISECONDS);
            if (!EXPECTED_RES.equals(receiver.getOutput().trim())) {
                CLog.i("No support for Framework, creating a native device");
                return false;
            }
        } catch (TimeoutException | AdbCommandRejectedException | ShellCommandUnresponsiveException
                | IOException e) {
            CLog.e(e);
        }
        // We default to support for framework to get same behavior as before.
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setFastbootEnabled(boolean enable) {
        mFastbootEnabled = enable;
    }

    /**
     * Helper to device if it's a serial from a remotely connected device.
     * serial format of tcp device is <ip or locahost>:<port>
     */
    protected boolean isTcpDeviceSerial(String serial) {
        final String remotePattern = IPADDRESS_PATTERN + "(:)([0-9]{2,5})(\\b)";
        Pattern pattern = Pattern.compile(remotePattern);
        Matcher match = pattern.matcher(serial.trim());
        if (match.find()) {
            return true;
        }
        return false;
    }
}
