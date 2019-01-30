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

package com.android.tradefed.targetprep;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;

/**
 * A {@link ITargetPreparer} that switches to the specified user kind in setUp. By default it
 * remains in the current user, and no switching is performed.
 *
 * <p>Tries to restore device user state by switching back to the pre-execution current user.
 */
@OptionClass(alias = "switch-user-target-preparer")
public class SwitchUserTargetPreparer extends BaseTargetPreparer implements ITargetCleaner {
    private static final int USER_SYSTEM = 0; // From the UserHandle class.

    /** Parameters that specify which user to run the test module as. */
    public enum UserType {
        // TODO:(b/123077733) Add support for guest and secondary.

        /** current foreground user of the device */
        CURRENT,
        /** user flagged as primary on the device; most often primary = system user = user 0 */
        PRIMARY,
        /** system user = user 0 */
        SYSTEM
    }

    @Option(
        name = "user-type",
        description = "The type of user to switch to before the module run."
    )
    private UserType mUserToSwitchTo = UserType.CURRENT;

    private int mPreExecutionCurrentUser;

    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError, DeviceNotAvailableException {

        mPreExecutionCurrentUser = device.getCurrentUser();

        switch (mUserToSwitchTo) {
            case SYSTEM:
                switchToUser(USER_SYSTEM, device);
                break;
            case PRIMARY:
                switchToUser(device.getPrimaryUserId(), device);
                break;
        }
    }

    private static void switchToUser(int userId, ITestDevice device)
            throws TargetSetupError, DeviceNotAvailableException {
        if (device.getCurrentUser() == userId) {
            return;
        }

        // Otherwise, switch to user with userId.
        if (device.switchUser(userId)) {
            // Successful switch.
            CLog.i("Switched to user %d.", userId);
        } else {
            // Couldn't switch, throw.
            throw new TargetSetupError(
                    String.format("Failed switch to user %d.", userId),
                    device.getDeviceDescriptor());
        }
    }

    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable e)
            throws DeviceNotAvailableException {
        // Restore the previous user as the foreground.
        if (!device.switchUser(mPreExecutionCurrentUser)) {
            CLog.w("Could not switch back to the user id: %d", mPreExecutionCurrentUser);
        }
    }
}
