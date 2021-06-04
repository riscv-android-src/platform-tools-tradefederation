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

import com.android.tradefed.command.ICommandOptions;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.service.IRemoteFeature;

import com.proto.tradefed.feature.ErrorInfo;
import com.proto.tradefed.feature.FeatureRequest;
import com.proto.tradefed.feature.FeatureResponse;

import java.lang.reflect.Field;
import java.util.Collection;

/**
 * Service implementation that returns the command options value of a given invocation.
 * This can be extended in the future to support more options.
 */
public class CommandOptionsGetter implements IRemoteFeature, IConfigurationReceiver {

    public static final String COMMAND_OPTIONS_GETTER = "getCommandOptions";
    public static final String OPTION_NAME = "option_name";

    private IConfiguration mConfig;

    @Override
    public String getName() {
        return COMMAND_OPTIONS_GETTER;
    }

    @Override
    public void setConfiguration(IConfiguration configuration) {
        mConfig = configuration;
    }

    @Override
    public FeatureResponse execute(FeatureRequest request) {
        FeatureResponse.Builder responseBuilder = FeatureResponse.newBuilder();
        if (mConfig == null) {
            responseBuilder.setErrorInfo(
                    ErrorInfo.newBuilder().setErrorTrace("Internal error, configuration not set."));
            return responseBuilder.build();
        }
        if (request.getArgsMap().isEmpty() || !request.getArgsMap().containsKey(OPTION_NAME)) {
            responseBuilder.setErrorInfo(
                    ErrorInfo.newBuilder().setErrorTrace("No option_name specified in the args."));
            return responseBuilder.build();
        }
        ICommandOptions commandOptions = mConfig.getCommandOptions();
        Collection<Field> allFields = OptionSetter.getOptionFieldsForClass(
                commandOptions.getClass());
        for (Field field : allFields) {
            final Option option = field.getAnnotation(Option.class);
            if (option.name().equals(request.getArgsMap().get(OPTION_NAME))) {
                Object fieldValue = OptionSetter.getFieldValue(field, commandOptions);
                if (fieldValue != null) {
                    responseBuilder.setResponse(fieldValue.toString());
                    return responseBuilder.build();
                }
            }
        }
        responseBuilder.setErrorInfo(
                ErrorInfo.newBuilder().setErrorTrace(
                        String.format("No option or not value set for '%s'",
                                request.getArgsMap().get(OPTION_NAME))));
        return responseBuilder.build();
    }
}
