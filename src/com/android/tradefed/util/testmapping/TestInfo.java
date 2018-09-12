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
package com.android.tradefed.util.testmapping;

import static com.google.common.base.Preconditions.checkState;

import com.android.tradefed.log.LogUtil.CLog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Stores the test information set in a TEST_MAPPING file. */
public class TestInfo {
    private String mName = null;
    private List<TestOption> mOptions = new ArrayList<TestOption>();

    public TestInfo(String name) {
        mName = name;
    }

    public String getName() {
        return mName;
    }

    public void addOption(TestOption option) {
        mOptions.add(option);
    }

    public List<TestOption> getOptions() {
        return mOptions;
    }

    /**
     * Merge with another test.
     *
     * <p>Update test options so the test has the coverage of both tests.
     *
     * <p>TODO(b/113616538): Implement a more robust option merging mechanism.
     *
     * @param test {@link TestInfo} object to be merged with.
     */
    public void merge(TestInfo test) {
        CLog.d("Merging test %s and %s.", this, test);
        // Merge can only happen for tests for the same module.
        checkState(
                mName.equals(test.getName()),
                "Only TestInfo for the same module can be " + "merged.");

        List<TestOption> mergedOptions = new ArrayList<>();

        // If any test only has exclusive options or no option, only keep the common exclusive
        // option in the merged test. For example:
        // this.mOptions: include-filter=value1, exclude-annotation=flaky
        // test.mOptions: exclude-annotation=flaky, exclude-filter=value2
        // merged options: exclude-annotation=flaky
        // Note that:
        // * The exclude-annotation of flaky is common between the two tests, so it's kept.
        // * The include-filter of value1 is dropped as `test` doesn't have any include-filter,
        //   thus it has larger test coverage and the include-filter is ignored.
        // * The exclude-filter of value2 is dropped as it's only for `test`. To achieve maximum
        //   test coverage for both `this` and `test`, we shall only keep the common exclusive
        //   filters.
        // * In the extreme case that one of the test has no option at all, the merged test will
        //   also have no option.
        if (test.exclusiveOptionsOnly() || this.exclusiveOptionsOnly()) {
            Set<TestOption> commonOptions = new HashSet<TestOption>(test.getOptions());
            commonOptions.retainAll(new HashSet<TestOption>(mOptions));
            mOptions = new ArrayList<TestOption>(commonOptions);
            CLog.d("Options are merged, updated test: %s.", this);
            return;
        }

        // When neither test has no option or with only exclusive options, we try the best to
        // merge the test options so the merged test will cover both tests.
        // 1. Keep all non-exclusive options
        // 2. Keep common exclusive options
        // For example:
        // this.mOptions: include-filter=value1, exclude-annotation=flaky
        // test.mOptions: exclude-annotation=flaky, exclude-filter=value2, include-filter=value3
        // merged options: exclude-annotation=flaky, include-filter=value1, include-filter=value3
        // Note that:
        // * The exclude-annotation of flaky is common between the two tests, so it's kept.
        // * The include-filter of value1 and value3 are both kept so the merged test will cover
        //   both tests.
        // * The exclude-filter of value2 is dropped as it's only for `test`. To achieve maximum
        //   test coverage for both `this` and `test`, we shall only keep the common exclusive
        //   filters.

        // Options from this test:
        Set<TestOption> nonExclusiveOptions =
                mOptions.stream()
                        .filter(option -> !option.isExclusive())
                        .collect(Collectors.toSet());
        Set<TestOption> exclusiveOptions =
                mOptions.stream()
                        .filter(option -> option.isExclusive())
                        .collect(Collectors.toSet());
        // Options from TestInfo to be merged:
        Set<TestOption> nonExclusiveOptionsToMerge =
                test.getOptions()
                        .stream()
                        .filter(option -> !option.isExclusive())
                        .collect(Collectors.toSet());
        Set<TestOption> exclusiveOptionsToMerge =
                test.getOptions()
                        .stream()
                        .filter(option -> option.isExclusive())
                        .collect(Collectors.toSet());

        nonExclusiveOptions.addAll(nonExclusiveOptionsToMerge);
        for (TestOption option : nonExclusiveOptions) {
            mergedOptions.add(option);
        }
        exclusiveOptions.retainAll(exclusiveOptionsToMerge);
        for (TestOption option : exclusiveOptions) {
            mergedOptions.add(option);
        }
        this.mOptions = mergedOptions;
        CLog.d("Options are merged, updated test: %s.", this);
    }

    /* Check if the TestInfo only has exclusive options.
     *
     * @return true if the TestInfo only has exclusive options.
     */
    private boolean exclusiveOptionsOnly() {
        for (TestOption option : mOptions) {
            if (option.isInclusive()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder options = new StringBuilder();
        for (TestOption option : mOptions) {
            options.append(option.toString());
        }
        return String.format("%s: Options: %s", mName, options);
    }
}
