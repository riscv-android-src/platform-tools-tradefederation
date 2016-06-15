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

import junit.framework.TestCase;

import org.easymock.EasyMock;

/**
 * Unit Tests for {@link ManagedTestDeviceFactory}
 */
public class ManagedTestDeviceFactoryTest extends TestCase {

    ManagedTestDeviceFactory mFactory;
    IDeviceManager mMockDeviceManager;
    IDeviceMonitor mMockDeviceMonitor;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockDeviceManager = EasyMock.createMock(IDeviceManager.class);
        mMockDeviceMonitor = EasyMock.createMock(IDeviceMonitor.class);
        mFactory = new ManagedTestDeviceFactory(true, mMockDeviceManager, mMockDeviceMonitor) ;
    }

    public void testIsSerialTcpDevice() {
        String input = "127.0.0.1:5555";
        assertTrue(mFactory.isTcpDeviceSerial(input));
    }

    public void testIsSerialTcpDevice_localhost() {
        String input = "localhost:54014";
        assertTrue(mFactory.isTcpDeviceSerial(input));
    }

    public void testIsSerialTcpDevice_notTcp() {
        String input = "00bf84d7d084cc84";
        assertFalse(mFactory.isTcpDeviceSerial(input));
    }

    public void testIsSerialTcpDevice_malformedPort() {
        String input = "127.0.0.1:999989";
        assertFalse(mFactory.isTcpDeviceSerial(input));
    }

    public void testIsSerialTcpDevice_nohost() {
        String input = ":5555";
        assertFalse(mFactory.isTcpDeviceSerial(input));
    }
}
