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
package com.android.tradefed.testtype.suite;

import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.util.TestMapping;

import java.util.LinkedHashMap;
import java.util.Set;

/**
 * Implementation of {@link BaseTestSuite} to run tests specified by option include-filter, or
 * TEST_MAPPING files from build, as a suite.
 */
public class TestMappingSuiteRunner extends BaseTestSuite {

    @Option(
        name = "test-mapping-test-type",
        description =
                "Type of tests to run, e.g., presubmit, postsubmit. The suite runner "
                        + "shall load the tests defined in all TEST_MAPPING files in the source "
                        + "code, through build artifact test_mappings.zip."
    )
    private String mTestType = null;

    /**
     * Load the tests configuration that will be run. Each tests is defined by a {@link
     * IConfiguration} and a unique name under which it will report results. There are 2 ways to
     * load tests for {@link TestMappingSuiteRunner}:
     *
     * <p>1. --test-mapping-test-type, which specifies the type of tests in TEST_MAPPING files. The
     * runner will parse all TEST_MAPPING files in the source code through build artifact
     * test_mappings.zip, and load tests grouped under the given test type.
     *
     * <p>2. --include-filter, which specifies the name of the test to run. The use case is for
     * presubmit check to only run a list of tests related to the Cls to be verifies. The list of
     * tests are compiled from the related TEST_MAPPING files in modified source code.
     *
     * @return a map of test name to the {@link IConfiguration} object of each test.
     */
    @Override
    public LinkedHashMap<String, IConfiguration> loadTests() {
        Set<String> includeFilter = getIncludeFilter();
        if (mTestType == null && includeFilter.isEmpty()) {
            throw new RuntimeException(
                    "At least one of the options, --test-mapping-test-type or --include-filter, "
                            + "should be set.");
        }
        if (mTestType != null && !includeFilter.isEmpty()) {
            throw new RuntimeException(
                    "If options --test-mapping-test-type is set, option --include-filter should "
                            + "not be set.");
        }

        if (mTestType != null) {
            Set<String> testsToRun = TestMapping.getTests(getBuildInfo(), mTestType);
            if (testsToRun.isEmpty()) {
                throw new RuntimeException(
                        String.format("No test found for the given type: %s.", mTestType));
            }
            setIncludeFilter(testsToRun);
        }

        return super.loadTests();
    }
}
