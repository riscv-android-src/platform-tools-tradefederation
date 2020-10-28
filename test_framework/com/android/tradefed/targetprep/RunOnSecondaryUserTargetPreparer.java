/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.UserInfo;
import com.android.tradefed.invoker.TestInformation;

import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * An {@link ITargetPreparer} that creates a secondary user in setup, and marks that tests should be
 * run in that user.
 *
 * <p>In teardown, the secondary user is removed.
 */
@OptionClass(alias = "run-on-secondary-user")
public class RunOnSecondaryUserTargetPreparer extends BaseTargetPreparer {

    @VisibleForTesting static final String RUN_TESTS_AS_USER_KEY = "RUN_TESTS_AS_USER";

    @VisibleForTesting static final String TEST_PACKAGE_NAME_OPTION = "test-package-name";

    @Option(
            name = TEST_PACKAGE_NAME_OPTION,
            description =
                    "the name of a package to be installed on the secondary user. "
                            + "This must already be installed on the device.",
            importance = Option.Importance.IF_UNSET)
    private List<String> mTestPackages = new ArrayList<>();

    @Override
    public void setUp(TestInformation testInfo)
            throws TargetSetupError, DeviceNotAvailableException {
        int secondaryUserId = getSecondaryUserId(testInfo.getDevice());

        if (secondaryUserId != -1) {
            // There is already a secondary user - so we don't want to remove it
            setDisableTearDown(true);
        } else {
            secondaryUserId = createSecondaryUser(testInfo.getDevice());
        }

        for (String pkg : mTestPackages) {
            testInfo.getDevice()
                    .executeShellCommand(
                            "pm install-existing --user " + secondaryUserId + " " + pkg);
        }

        testInfo.properties().put(RUN_TESTS_AS_USER_KEY, Integer.toString(secondaryUserId));
    }

    /** Get the id of a secondary user currently on the device. -1 if there is none */
    private static int getSecondaryUserId(ITestDevice device) throws DeviceNotAvailableException {
        for (Map.Entry<Integer, UserInfo> userInfo : device.getUserInfos().entrySet()) {
            if (userInfo.getValue().isSecondary()) {
                return userInfo.getKey();
            }
        }
        return -1;
    }

    /** Creates a secondary user and returns the new user ID. */
    private static int createSecondaryUser(ITestDevice device) throws DeviceNotAvailableException {
        final String createUserOutput = device.executeShellCommand("pm create-user secondary");
        final int userId = Integer.parseInt(createUserOutput.split(" id ")[1].trim());
        device.executeShellCommand("am start-user -w " + userId);
        return userId;
    }

    @Override
    public void tearDown(TestInformation testInfo, Throwable e) throws DeviceNotAvailableException {
        int userId = Integer.parseInt(testInfo.properties().get(RUN_TESTS_AS_USER_KEY));

        testInfo.getDevice().removeUser(userId);
    }
}
