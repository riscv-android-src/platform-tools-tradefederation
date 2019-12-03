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
package com.android.tradefed.testtype.rust;

import com.android.ddmlib.IShellOutputReceiver;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IRemoteTest;

import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;

/** Base class of RustBinaryHostTest and RustBinaryTest */
@OptionClass(alias = "rust-test")
public abstract class RustTestBase implements IRemoteTest {

    @Option(
            name = "test-options",
            description = "Option string to be passed to the binary when running")
    protected List<String> mTestOptions = new ArrayList<>();

    @Option(
            name = "test-timeout",
            description = "Timeout for a single test file to terminate.",
            isTimeVal = true)
    protected long mTestTimeout = 20 * 1000L; // milliseconds

    // A wrapper that can be redefined in unit tests to create a (mocked) result parser.
    @VisibleForTesting
    IShellOutputReceiver createParser(ITestInvocationListener listener, String runName) {
        return new RustTestResultParser(listener, runName);
    }
}
