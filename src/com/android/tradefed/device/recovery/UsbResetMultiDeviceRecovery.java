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
package com.android.tradefed.device.recovery;

import com.android.helper.aoa.UsbDevice;
import com.android.helper.aoa.UsbException;
import com.android.helper.aoa.UsbHelper;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceAllocationState;
import com.android.tradefed.device.DeviceManager;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.FastbootHelper;
import com.android.tradefed.device.IManagedTestDevice;
import com.android.tradefed.device.IMultiDeviceRecovery;
import com.android.tradefed.device.INativeDevice;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.StubDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.RunUtil;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** A {@link IMultiDeviceRecovery} which resets USB buses for offline devices. */
@OptionClass(alias = "usb-recovery")
public class UsbResetMultiDeviceRecovery implements IMultiDeviceRecovery {

    @Option(name = "disable", description = "Disable the device recoverer.")
    private boolean mDisable = false;

    private String mFastbootPath = "fastboot";

    @Override
    public void setFastbootPath(String fastbootPath) {
        mFastbootPath = fastbootPath;
    }

    @Override
    public void recoverDevices(List<IManagedTestDevice> devices) {
        if (mDisable) {
            return; // Skip device recovery
        }

        FastbootHelper fastboot = getFastbootHelper();
        Set<String> fastbootSerials;

        // Track devices in an Unknown, Unavailable, or otherwise suspicious state
        List<IManagedTestDevice> devicesToReset = new ArrayList<>();

        fastbootSerials = fastboot.getDevices();
        for (IManagedTestDevice device : devices) {
            // Make sure not to skip Fastboot devices
            if (device.getIDevice() instanceof StubDevice
                    && !(device.getIDevice() instanceof DeviceManager.FastbootDevice)) {
                continue;
            }

            DeviceAllocationState state = device.getAllocationState();
            // Always reset Unknown or Unavailable devices
            if (DeviceAllocationState.Unknown.equals(state)
                    || DeviceAllocationState.Unavailable.equals(state)
                    // If device is in Fastboot but unallocated, this is suspicious and it is reset
                    || (fastbootSerials.contains(device.getSerialNumber())
                            && !DeviceAllocationState.Allocated.equals(state))) {
                devicesToReset.add(device);
            }
        }

        if (devicesToReset.isEmpty()) {
            return; // No devices to recover
        }

        // Reset USB ports
        resetUsbPorts(devicesToReset);

        // Reboot devices
        fastbootSerials = fastboot.getDevices();
        for (ITestDevice device : devicesToReset) {
            if (fastbootSerials.contains(device.getSerialNumber())) {
                continue; // Skip Fastboot devices as they may require additional recovery steps
            }

            try {
                device.reboot();
            } catch (DeviceNotAvailableException e) {
                CLog.e(e);
                CLog.w(
                        "Device '%s' did not come back online after USB reset.",
                        device.getSerialNumber());
            }
        }
    }

    // Perform a USB port reset on all the specified devices
    private void resetUsbPorts(List<? extends INativeDevice> devices) {
        List<String> deviceSerials = Lists.transform(devices, INativeDevice::getSerialNumber);

        try (UsbHelper usb = getUsbHelper()) {
            for (String serial : deviceSerials) {
                try (UsbDevice device = usb.getDevice(serial)) {
                    if (device != null) {
                        device.reset();
                    } else {
                        CLog.w("Device '%s' not found during USB reset.", serial);
                    }
                }
            }
        } catch (UsbException e) {
            CLog.w("Failed to reset USB ports.");
            CLog.e(e);
        }
    }

    @VisibleForTesting
    FastbootHelper getFastbootHelper() {
        return new FastbootHelper(RunUtil.getDefault(), mFastbootPath);
    }

    @VisibleForTesting
    UsbHelper getUsbHelper() {
        return new UsbHelper();
    }
}
