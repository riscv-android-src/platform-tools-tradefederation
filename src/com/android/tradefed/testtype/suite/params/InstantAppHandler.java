/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tradefed.testtype.suite.params;

import com.android.tradefed.config.IConfiguration;

/** Handler for {@link ModuleParameters#INSTANT_APP}. */
public class InstantAppHandler implements IModuleParameter {

    /** {@inheritDoc} */
    @Override
    public String getParameterIdentifier() {
        return "instant";
    }

    /** {@inheritDoc} */
    @Override
    public void applySetup(IConfiguration moduleConfiguration) {
        // TODO:Add the special setup as described below.
        // First, force target_preparers if they support it to install app in instant mode.

        // Second, notify HostTest that instant mode might be needed for apks.

        // Third, add filter to exclude @FullAppMode
    }
}
