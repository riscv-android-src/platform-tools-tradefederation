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

/**
 * Handler that specify that nothing should be done and the parameter should not create any extra
 * module.
 */
public class NegativeHandler implements IModuleParameterHandler {

    @Override
    public String getParameterIdentifier() {
        throw new RuntimeException("Should never be called");
    }

    @Override
    public void applySetup(IConfiguration moduleConfiguration) {
        throw new RuntimeException("Should never be called");
    }

    @Override
    public void addParameterSpecificConfig(IConfiguration moduleConfiguration) {
        throw new RuntimeException("Should never be called");
    }
}
