/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tradefed.config.filter;

import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.service.TradefedFeatureClient;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.ITestFilterReceiver;
import com.android.tradefed.testtype.suite.BaseTestSuite;
import com.android.tradefed.testtype.suite.SuiteTestFilter;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.proto.tradefed.feature.FeatureResponse;
import com.proto.tradefed.feature.PartResponse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Filter options applied to the invocation. */
@OptionClass(alias = "global-filters")
public final class GlobalTestFilter {

    public static final String INCLUDE_FILTER_OPTION = "include-filter";
    public static final String EXCLUDE_FILTER_OPTION = "exclude-filter";

    @Option(
            name = INCLUDE_FILTER_OPTION,
            description =
                    "Filters applied to the invocation. Format: [abi] [module-name]"
                            + " [test-class][#method-name]")
    private Set<String> mIncludeFilters = new LinkedHashSet<>();

    @Option(
            name = EXCLUDE_FILTER_OPTION,
            description =
                    "Filters applied to the invocation. Format: [abi] [module-name]"
                            + " [test-class][#method-name]")
    private Set<String> mExcludeFilters = new LinkedHashSet<>();

    @Option(
            name = "disable-global-filters",
            description = "Feature flag to enable the global filters")
    private boolean mDisable = false;

    private TradefedFeatureClient mClient;
    private boolean mSetupDone = false;

    public GlobalTestFilter() {}

    @VisibleForTesting
    GlobalTestFilter(TradefedFeatureClient client) {
        mClient = client;
    }

    /** Returns the Set of global include filters. */
    public Set<String> getIncludeFilters() {
        return new LinkedHashSet<>(mIncludeFilters);
    }

    /** Returns the Set of global exclude filters. */
    public Set<String> getExcludeFilters() {
        return new LinkedHashSet<>(mExcludeFilters);
    }

    public void addPreviousPassedTests(Set<String> previousPassed) {
        if (!previousPassed.isEmpty()) {
            CLog.d("Adding following exclusion to GlobalTestFilter: %s", previousPassed);
        }
        mExcludeFilters.addAll(previousPassed);
    }

    /** Initialize the global filters by passing them to the tests. */
    public void setUpFilters(IConfiguration config) {
        if (mDisable) {
            CLog.d("Global filters are disabled.");
            return;
        }
        if (mSetupDone) {
            CLog.d("Global filters already set.");
            return;
        }
        CLog.d("Setting up global filters");
        // If it's a subprocess, fetch filters
        if (config.getCommandOptions().getInvocationData().containsKey("subprocess")) {
            populateGlobalFilters();
        }
        // Apply filters
        for (IRemoteTest test : config.getTests()) {
            if (test instanceof BaseTestSuite) {
                ((BaseTestSuite) test).setIncludeFilter(mIncludeFilters);
                ((BaseTestSuite) test).setExcludeFilter(mExcludeFilters);
            } else if (test instanceof ITestFilterReceiver) {
                ITestFilterReceiver filterableTest = (ITestFilterReceiver) test;
                applyFiltersToTest(filterableTest);
            }
        }
        mSetupDone = true;
    }

    /** Apply the global filters to the test. */
    public void applyFiltersToTest(ITestFilterReceiver filterableTest) {
        Set<String> includeFilters = new LinkedHashSet<>(filterableTest.getIncludeFilters());
        includeFilters.addAll(filtersFromGlobal(mIncludeFilters));
        filterableTest.clearIncludeFilters();
        filterableTest.addAllIncludeFilters(includeFilters);

        Set<String> excludeFilters = new LinkedHashSet<>(filterableTest.getExcludeFilters());
        excludeFilters.addAll(filtersFromGlobal(mExcludeFilters));
        filterableTest.clearExcludeFilters();
        filterableTest.addAllExcludeFilters(excludeFilters);
    }

    /** Apply global filters to the suite */
    public void applyFiltersToTest(BaseTestSuite suite) {
        suite.setIncludeFilter(mIncludeFilters);
        suite.setExcludeFilter(mExcludeFilters);
        suite.reevaluateFilters();
    }

    /** Fetch and populate global filters if needed. */
    private void populateGlobalFilters() {
        if (mClient == null) {
            mClient = new TradefedFeatureClient();
        }
        try {
            FeatureResponse globalFilters =
                    mClient.triggerFeature(
                            GlobalFilterGetter.GLOBAL_FILTER_GETTER, new HashMap<>());
            if (globalFilters.hasMultiPartResponse()) {
                for (PartResponse rep :
                        globalFilters.getMultiPartResponse().getResponsePartList()) {
                    if (rep.getKey().equals(INCLUDE_FILTER_OPTION)) {
                        mIncludeFilters.addAll(splitStringFilters(rep.getValue()));
                    } else if (rep.getKey().equals(EXCLUDE_FILTER_OPTION)) {
                        mExcludeFilters.addAll(splitStringFilters(rep.getValue()));
                    } else {
                        CLog.w("Unexpected response key '%s' for global filters", rep.getKey());
                    }
                }
            } else {
                CLog.w("Unexpected response for global filters");
            }
        } finally {
            mClient.close();
        }
    }

    private List<String> splitStringFilters(String value) {
        if (Strings.isNullOrEmpty(value)) {
            return new ArrayList<String>();
        }
        return Arrays.asList(value.split(","));
    }

    private Set<String> filtersFromGlobal(Set<String> filters) {
        Set<String> globalFilters = new LinkedHashSet<>();
        filters.forEach(f->{
                    SuiteTestFilter suiteFilter = SuiteTestFilter.createFrom(f);
                    if (!Strings.isNullOrEmpty(suiteFilter.getTest())) {
                        globalFilters.add(suiteFilter.getTest());
                    }
                }
        );
        return globalFilters;
    }
}
