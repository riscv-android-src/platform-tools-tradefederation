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
package com.android.tradefed.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration Object that describes some aspect of the configuration itself. Like a membership
 * test-suite-tag. This class cannot receive option values via command line. Only directly in the
 * xml.
 */
public class ConfigurationDescriptor {

    @Option(name = "test-suite-tag", description = "A membership tag to suite. Can be repeated.")
    private List<String> mSuiteTags = new ArrayList<>();

    /** Returns the list of suite tags the test is part of. */
    public List<String> getSuiteTags() {
        return mSuiteTags;
    }

    /** Sets the list of suite tags the test is part of. */
    public void setSuiteTags(List<String> suiteTags) {
        mSuiteTags = suiteTags;
    }
}
