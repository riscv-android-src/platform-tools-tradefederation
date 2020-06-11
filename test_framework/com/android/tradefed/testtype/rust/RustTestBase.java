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
import com.android.tradefed.testtype.ITestFilterReceiver;

import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Base class of RustBinaryHostTest and RustBinaryTest */
@OptionClass(alias = "rust-test")
public abstract class RustTestBase implements IRemoteTest, ITestFilterReceiver {

    @Option(
            name = "test-options",
            description = "Option string to be passed to the binary when running")
    protected List<String> mTestOptions = new ArrayList<>();

    @Option(
            name = "test-timeout",
            description = "Timeout for a single test file to terminate.",
            isTimeVal = true)
    protected long mTestTimeout = 20 * 1000L; // milliseconds

    private Set<String> mIncludeFilters = new LinkedHashSet<>();
    private Set<String> mExcludeFilters = new LinkedHashSet<>();

    // A wrapper that can be redefined in unit tests to create a (mocked) result parser.
    @VisibleForTesting
    IShellOutputReceiver createParser(ITestInvocationListener listener, String runName) {
        return new RustTestResultParser(listener, runName);
    }

    // TODO(b/145607401): make rust test runners accept filters
    // Now the following are just dummy methods,
    // to shut off run-time warning about not implementing ITestFilterReceiver.

    /** {@inheritDoc} */
    @Override
    public void addIncludeFilter(String filter) {
        mIncludeFilters.add(filter);
    }

    /** {@inheritDoc} */
    @Override
    public void addExcludeFilter(String filter) {
        mExcludeFilters.add(filter);
    }

    /** {@inheritDoc} */
    @Override
    public void addAllIncludeFilters(Set<String> filters) {
        mIncludeFilters.addAll(filters);
    }

    /** {@inheritDoc} */
    @Override
    public void addAllExcludeFilters(Set<String> filters) {
        mExcludeFilters.addAll(filters);
    }

    /** {@inheritDoc} */
    @Override
    public void clearIncludeFilters() {
        mIncludeFilters.clear();
    }

    /** {@inheritDoc} */
    @Override
    public void clearExcludeFilters() {
        mExcludeFilters.clear();
    }

    /** {@inheritDoc} */
    @Override
    public Set<String> getIncludeFilters() {
        return mIncludeFilters;
    }

    /** {@inheritDoc} */
    @Override
    public Set<String> getExcludeFilters() {
        return mExcludeFilters;
    }
}
