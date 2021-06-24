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
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.service.IRemoteFeature;

import com.google.common.base.Joiner;
import com.proto.tradefed.feature.FeatureRequest;
import com.proto.tradefed.feature.FeatureResponse;
import com.proto.tradefed.feature.MultiPartResponse;
import com.proto.tradefed.feature.PartResponse;

/** Service implementation that returns the filters of a given invocation. */
public class GlobalFilterGetter implements IRemoteFeature, IConfigurationReceiver {

    public static final String GLOBAL_FILTER_GETTER = "getGlobalFilters";

    private IConfiguration mConfig;

    @Override
    public String getName() {
        return GLOBAL_FILTER_GETTER;
    }

    @Override
    public void setConfiguration(IConfiguration configuration) {
        mConfig = configuration;
    }

    @Override
    public FeatureResponse execute(FeatureRequest request) {
        FeatureResponse.Builder responseBuilder = FeatureResponse.newBuilder();
        if (mConfig != null) {
            MultiPartResponse.Builder multiPartBuilder = MultiPartResponse.newBuilder();
            multiPartBuilder.addResponsePart(
                    PartResponse.newBuilder()
                            .setKey(GlobalTestFilter.INCLUDE_FILTER_OPTION)
                            .setValue(
                                    Joiner.on(",")
                                            .join(mConfig.getGlobalFilters().getIncludeFilters())));
            multiPartBuilder.addResponsePart(
                    PartResponse.newBuilder()
                            .setKey(GlobalTestFilter.EXCLUDE_FILTER_OPTION)
                            .setValue(
                                    Joiner.on(",")
                                            .join(mConfig.getGlobalFilters().getExcludeFilters())));
            responseBuilder.setMultiPartResponse(multiPartBuilder);
        }
        return responseBuilder.build();
    }
}
