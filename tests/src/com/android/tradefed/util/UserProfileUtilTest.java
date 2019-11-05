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

package com.android.tradefed.util;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;

/** Unit tests for {@link UserProfileUtil}. */
public class UserProfileUtilTest {
    @Mock public ITestDevice mDevice;

    @Rule public ExpectedException thrown = ExpectedException.none();

    private static final int WORK_USER_ID = 10;
    // Sample outputs for the command to list users based on true events.
    private static final String SINGLE_USER_OUTPUT =
            String.format(
                    "Users:\n" + "\t\tUserInfo{%d:Owner:c13} running",
                    UserProfileUtil.SYSTEM_USER_ID);
    private static final String TWO_USERS_OUTPUT =
            String.format(
                    "Users:\n"
                            + "\t\tUserInfo{%d:Owner:c13} running\n"
                            + "\t\tUserInfo{%d:WorkProfile:20} running",
                    UserProfileUtil.SYSTEM_USER_ID, WORK_USER_ID);

    @Before
    public void setUp() {
        initMocks(this);
        // Clear the cached parsing results in the utility.
        UserProfileUtil.sUserProfiles.clear();
    }

    @Test
    public void testListUserCommandCalled() throws DeviceNotAvailableException {
        doReturn(TWO_USERS_OUTPUT)
                .when(mDevice)
                .executeShellCommand(UserProfileUtil.LIST_USERS_COMMAND);
        UserProfileUtil.getUserProfile(mDevice, UserProfileUtil.ProfileType.PRIMARY);
        verify(mDevice).executeShellCommand(UserProfileUtil.LIST_USERS_COMMAND);
    }

    @Test
    public void testParsingPrimaryProfile() throws DeviceNotAvailableException {
        doReturn(TWO_USERS_OUTPUT)
                .when(mDevice)
                .executeShellCommand(UserProfileUtil.LIST_USERS_COMMAND);
        UserProfileUtil.UserProfile user =
                UserProfileUtil.getUserProfile(mDevice, UserProfileUtil.ProfileType.PRIMARY);
        Assert.assertEquals(UserProfileUtil.ProfileType.PRIMARY, user.type);
        Assert.assertEquals(UserProfileUtil.SYSTEM_USER_ID, user.userId);
    }

    @Test
    public void testParsingWorkProfile() throws DeviceNotAvailableException {
        doReturn(TWO_USERS_OUTPUT)
                .when(mDevice)
                .executeShellCommand(UserProfileUtil.LIST_USERS_COMMAND);
        UserProfileUtil.UserProfile user =
                UserProfileUtil.getUserProfile(mDevice, UserProfileUtil.ProfileType.WORK);
        Assert.assertEquals(UserProfileUtil.ProfileType.WORK, user.type);
        Assert.assertEquals(WORK_USER_ID, user.userId);
    }

    @Test
    public void testNotFindingUserThrows() throws DeviceNotAvailableException {
        thrown.expectMessage("Could not find user");
        doReturn(SINGLE_USER_OUTPUT)
                .when(mDevice)
                .executeShellCommand(UserProfileUtil.LIST_USERS_COMMAND);
        UserProfileUtil.UserProfile user =
                UserProfileUtil.getUserProfile(mDevice, UserProfileUtil.ProfileType.WORK);
    }

    @Test
    public void testUsersAlreadyFoundAreCached() throws DeviceNotAvailableException {
        doReturn(TWO_USERS_OUTPUT)
                .when(mDevice)
                .executeShellCommand(UserProfileUtil.LIST_USERS_COMMAND);
        for (int i = 0; i < 3; i++) {
            UserProfileUtil.UserProfile user =
                    UserProfileUtil.getUserProfile(mDevice, UserProfileUtil.ProfileType.WORK);
        }
        verify(mDevice, times(1)).executeShellCommand(UserProfileUtil.LIST_USERS_COMMAND);
    }
}
