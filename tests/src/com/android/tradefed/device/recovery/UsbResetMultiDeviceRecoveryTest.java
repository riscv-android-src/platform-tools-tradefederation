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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.android.helper.aoa.UsbHelper;
import com.android.tradefed.device.DeviceAllocationState;
import com.android.tradefed.device.DeviceManager.FastbootDevice;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.FastbootHelper;
import com.android.tradefed.device.IManagedTestDevice;
import com.android.tradefed.device.StubDevice;

import com.google.common.collect.ImmutableSet;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Answers;

import java.util.Arrays;
import java.util.HashSet;

/** Unit tests for {@link UsbResetMultiDeviceRecovery}. */
@RunWith(JUnit4.class)
public class UsbResetMultiDeviceRecoveryTest {

    private static final String SERIAL = "SERIAL";

    private UsbResetMultiDeviceRecovery mRecoverer;

    private IManagedTestDevice mDevice;
    private FastbootHelper mFastboot;
    private UsbHelper mUsb;

    @Before
    public void setUp() {
        mDevice = mock(IManagedTestDevice.class);
        when(mDevice.getSerialNumber()).thenReturn(SERIAL);

        mFastboot = mock(FastbootHelper.class);
        mUsb = mock(UsbHelper.class, Answers.RETURNS_DEEP_STUBS);

        mRecoverer =
                new UsbResetMultiDeviceRecovery() {
                    @Override
                    FastbootHelper getFastbootHelper() {
                        return mFastboot;
                    }

                    @Override
                    UsbHelper getUsbHelper() {
                        return mUsb;
                    }
                };
    }

    /** Test that a stub device is not considered for recovery. */
    @Test
    public void testStubDevice() throws DeviceNotAvailableException {
        when(mDevice.getIDevice()).thenReturn(new StubDevice(SERIAL));

        mRecoverer.recoverDevices(Arrays.asList(mDevice));

        verifyZeroInteractions(mUsb);
        verify(mDevice, never()).reboot();
    }

    /** Test that an allocated fastboot device is not considered for recovery. */
    @Test
    public void testFastbootDevice_found_allocated() throws DeviceNotAvailableException {
        when(mDevice.getIDevice()).thenReturn(new FastbootDevice(SERIAL));
        when(mDevice.getAllocationState()).thenReturn(DeviceAllocationState.Allocated);
        // found in fastboot
        when(mFastboot.getDevices()).thenReturn(ImmutableSet.of(SERIAL));

        mRecoverer.recoverDevices(Arrays.asList(mDevice));

        verifyZeroInteractions(mUsb);
        verify(mDevice, never()).reboot();
    }

    /** Test that an unavailable fastboot device is reset but not rebooted. */
    @Test
    public void testFastbootDevice_found_unavailable() throws DeviceNotAvailableException {
        when(mDevice.getIDevice()).thenReturn(new FastbootDevice(SERIAL));
        when(mDevice.getAllocationState()).thenReturn(DeviceAllocationState.Unavailable);
        // found in fastboot
        when(mFastboot.getDevices()).thenReturn(ImmutableSet.of(SERIAL));

        mRecoverer.recoverDevices(Arrays.asList(mDevice));

        verify(mUsb, times(1)).getDevice(eq(SERIAL));
        verify(mUsb.getDevice(SERIAL), times(1)).reset();
        verify(mDevice, never()).reboot();
    }

    /** Test that an unavailable device not found in fastboot is reset and rebooted. */
    @Test
    public void testFastbootDevice_notFound_unavailable() throws DeviceNotAvailableException {
        when(mDevice.getIDevice()).thenReturn(new FastbootDevice(SERIAL));
        when(mDevice.getAllocationState()).thenReturn(DeviceAllocationState.Unavailable);
        // not found in fastboot
        when(mFastboot.getDevices()).thenReturn(new HashSet<>());

        mRecoverer.recoverDevices(Arrays.asList(mDevice));

        verify(mUsb, times(1)).getDevice(eq(SERIAL));
        verify(mUsb.getDevice(SERIAL), times(1)).reset();
        verify(mDevice, times(1)).reboot();
    }
}
