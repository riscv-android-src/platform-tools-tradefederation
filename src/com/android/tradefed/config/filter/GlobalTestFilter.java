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

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.service.TradefedFeatureClient;

import com.google.common.annotations.VisibleForTesting;
import com.proto.tradefed.feature.FeatureResponse;
import com.proto.tradefed.feature.PartResponse;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Filter options applied to the invocation. */
public final class GlobalTestFilter {

    public static final String INCLUDE_FILTER_OPTION = "include-filter";
    public static final String EXCLUDE_FILTER_OPTION = "exclude-filter";

    // TODO: Allow the option to be specified
    /*@Option(
    name = INCLUDE_FILTER_OPTION,
    description =
    "Filters applied to the invocation. Format: [abi] [module-name] [test-class][#method-name]")*/
    private Set<String> mIncludeFilters = new LinkedHashSet<>();

    /*@Option(
    name = EXCLUDE_FILTER_OPTION,
    description =
            "Filters applied to the invocation. Format: [abi] [module-name] [test-class][#method-name]")*/
    private Set<String> mExcludeFilters = new LinkedHashSet<>();

    private TradefedFeatureClient mClient;

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

    /** Fetch and populate global filters if needed. */
    public void populateGlobalFilters(String invocationId) {
        if (mClient == null) {
            mClient = new TradefedFeatureClient();
        }
        try {
            Map<String, String> args = new HashMap<>();
            args.put("invocation_id", invocationId);
            FeatureResponse globalFilters =
                    mClient.triggerFeature(GlobalFilterGetter.GLOBAL_FILTER_GETTER, args);
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
        return Arrays.asList(value.split("\n"));
    }
}
