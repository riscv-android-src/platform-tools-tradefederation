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

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;

import com.google.common.annotations.VisibleForTesting;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Utility that helps with user profiles on a device. */
public class UserProfileUtil {
    // Matches "UserInfo{<id>:<name>:<other info>}".
    private static final Pattern USER_INFO_PATTERN =
            Pattern.compile("UserInfo\\{(?<id>\\d+):(?<name>.*):.+\\}");
    @VisibleForTesting static final String LIST_USERS_COMMAND = "pm list users";
    // System user ID is 0, as documented at
    // http://source.android.com/devices/tech/admin/multi-user-testing#adb-interactions-across-users
    @VisibleForTesting static final int SYSTEM_USER_ID = 0;

    public static enum ProfileType {
        PRIMARY,
        WORK;
    }

    /** Represents a user profile. */
    public static class UserProfile {
        public final int userId;
        public final ProfileType type;

        public UserProfile(int userId, ProfileType type) {
            this.userId = userId;
            this.type = type;
        }
    }

    // Caches profile results to prevent repetitive command parsing.
    @VisibleForTesting
    static Map<ITestDevice, Map<ProfileType, UserProfile>> sUserProfiles = new HashMap<>();

    /**
     * Get a user profile.
     *
     * <p>This method uses the assumptions that the system user is the primary user and that there
     * are at most two user profiles on the device.
     *
     * @param device The device under test.
     * @param type The type of profile.
     */
    public static UserProfile getUserProfile(ITestDevice device, ProfileType type)
            throws DeviceNotAvailableException {
        sUserProfiles.computeIfAbsent(device, k -> new HashMap<ProfileType, UserProfile>());
        if (sUserProfiles.containsKey(device)) {
            if (sUserProfiles.get(device).containsKey(type)) {
                return sUserProfiles.get(device).get(type);
            }
        }
        // Dump the user information and parses the user id.
        String[] usersOutputLines = device.executeShellCommand(LIST_USERS_COMMAND).split("\n");
        for (String line : usersOutputLines) {
            Matcher matcher = USER_INFO_PATTERN.matcher(line);
            if (!matcher.find()) {
                continue;
            }
            int userId = Integer.parseInt(matcher.group("id"));
            if (userId == SYSTEM_USER_ID) {
                sUserProfiles
                        .get(device)
                        .put(ProfileType.PRIMARY, new UserProfile(userId, ProfileType.PRIMARY));
            } else {
                sUserProfiles
                        .get(device)
                        .put(ProfileType.WORK, new UserProfile(userId, ProfileType.WORK));
            }
        }
        UserProfile user = sUserProfiles.get(device).get(type);
        if (user == null) {
            throw new RuntimeException(
                    String.format(
                            "Could not find user profile of type %s on device %s.", type, device));
        }
        return user;
    }
}
