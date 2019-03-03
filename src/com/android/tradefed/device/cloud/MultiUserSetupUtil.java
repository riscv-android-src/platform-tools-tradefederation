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
package com.android.tradefed.device.cloud;

import com.android.tradefed.device.TestDeviceOptions;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;

/** Utility to create another user in Cuttlefish VM. New user will allow to run a second device. */
public class MultiUserSetupUtil {

    /** Setup a new remote user on an existing Cuttlefish VM. */
    public static CommandResult prepareRemoteUser(
            String username,
            GceAvdInfo remoteInstance,
            TestDeviceOptions options,
            IRunUtil runUtil,
            long timeoutMs) {
        // First create the user
        String createUserCommand = "sudo useradd " + username + " -G sudo -m -s /bin/bash -p '*'";
        CommandResult createUserRes =
                RemoteSshUtil.remoteSshCommandExec(
                        remoteInstance, options, runUtil, timeoutMs, createUserCommand.split(" "));
        if (!CommandStatus.SUCCESS.equals(createUserRes.getStatus())) {
            return createUserRes;
        }
        // Second setup the user
        String kvmGroup = "sudo usermod -aG kvm " + username;
        CommandResult kvmRes =
                RemoteSshUtil.remoteSshCommandExec(
                        remoteInstance, options, runUtil, timeoutMs, kvmGroup.split(" "));
        if (!CommandStatus.SUCCESS.equals(kvmRes.getStatus())) {
            return kvmRes;
        }
        String cvdNetwork = "sudo usermod -aG cvdnetwork " + username;
        CommandResult cvdRes =
                RemoteSshUtil.remoteSshCommandExec(
                        remoteInstance, options, runUtil, timeoutMs, cvdNetwork.split(" "));
        if (!CommandStatus.SUCCESS.equals(cvdRes.getStatus())) {
            return cvdRes;
        }
        return null;
    }

    /** Setup a new remote user on an existing Cuttlefish VM. */
    public static CommandResult prepareRemoteHomeDir(
            String mainRootUser,
            String username,
            GceAvdInfo remoteInstance,
            TestDeviceOptions options,
            IRunUtil runUtil,
            long timeoutMs) {
        // Copy main user directory to
        String copyDevice = "sudo ln -s /home/" + mainRootUser + "/* /home/" + username + "/";
        CommandResult copyRes =
                RemoteSshUtil.remoteSshCommandExec(
                        remoteInstance, options, runUtil, timeoutMs, copyDevice.split(" "));
        if (!CommandStatus.SUCCESS.equals(copyRes.getStatus())) {
            return copyRes;
        }
        // Permission
        String chownUser = "find /home/" + username + " | sudo xargs chown " + username;
        CommandResult chownRes =
                RemoteSshUtil.remoteSshCommandExec(
                        remoteInstance, options, runUtil, timeoutMs, chownUser);
        if (!CommandStatus.SUCCESS.equals(chownRes.getStatus())) {
            return chownRes;
        }
        return null;
    }
}
