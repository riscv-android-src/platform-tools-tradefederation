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

package com.android.tradefed.targetprep;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.AttenuatorUtil;
import com.android.tradefed.util.RunUtil;

import java.util.ArrayList;
import java.util.List;

@OptionClass(alias = "attenuator")
public class AttenuatorPreparer implements ITargetPreparer {
    @Option(name = "attenuator-value", description = "Initial value for the attenuator")
    private int initValue = 0;

    @Option(name = "disable", description = "Disable this preparer")
    private boolean mDisable = true;

    @Option(name = "attenuator-ip", description = "IP address for attenuators")
    private List<String> mIpAddresses = new ArrayList<String>();

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            DeviceNotAvailableException {
        if (mDisable)
            return;
        for (String ipAddress : mIpAddresses) {
            CLog.v("Initialize attenuator %s to %d at setup", mIpAddresses, initValue);
            AttenuatorUtil att = new AttenuatorUtil(ipAddress);
            att.setValue(initValue);
        }
    }
}

