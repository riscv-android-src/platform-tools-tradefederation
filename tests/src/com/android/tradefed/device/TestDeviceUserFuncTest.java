/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IDeviceTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Map;

/** Functional tests for the {@link ITestDevice} user management APIs */
@RunWith(DeviceJUnit4ClassRunner.class)
public class TestDeviceUserFuncTest implements IDeviceTest {
    private TestDevice mTestDevice;

    @Override
    public void setDevice(ITestDevice device) {
        mTestDevice = (TestDevice) device;
    }

    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }

    @Before
    public void setUp() throws Exception {
        // Ensure at set-up that the device is available.
        mTestDevice.waitForDeviceAvailable();
    }

    /**
     * Tests several of the user creation, listing, and deletion methods
     *
     * <p>Specifically this tests {@link ITestDevice#createUser(String, boolean, boolean)}, {@link
     * ITestDevice#listUsers()}, and {@link ITestDevice#getUserInfos()}.
     */
    @Test
    public void testUserLifecycle() throws Exception {
        int userId = -1;
        try {
            final String userName = "Google";
            userId = mTestDevice.createUser(userName, false, false);
            assertNotEquals(0, userId);
            assertNotEquals(-1, userId);

            List<Integer> listedIDs = mTestDevice.listUsers();
            boolean containsUserId = listedIDs.contains(userId);
            assertEquals(true, containsUserId);

            Map<Integer, UserInfo> userInfos = mTestDevice.getUserInfos();
            boolean userInfoContainsUserId = userInfos.containsKey(userId);
            assertEquals(true, userInfoContainsUserId);
            UserInfo info = userInfos.get(userId);
            assertNotNull(info);
            assertEquals(userName, info.userName());
        } finally {
            if (userId != -1) {
                mTestDevice.removeUser(userId);
            }
        }
    }
}
