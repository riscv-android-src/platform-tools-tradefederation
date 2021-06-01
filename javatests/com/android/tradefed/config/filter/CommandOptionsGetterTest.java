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

import static org.junit.Assert.assertEquals;

import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.OptionSetter;

import com.proto.tradefed.feature.FeatureRequest;
import com.proto.tradefed.feature.FeatureResponse;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link CommandOptionsGetter}.
 */
@RunWith(JUnit4.class)
public class CommandOptionsGetterTest {

    private IConfiguration mConfiguration;
    private CommandOptionsGetter mGetter;

    @Before
    public void setUp() {
        mConfiguration = new Configuration("name", "description");
        mGetter = new CommandOptionsGetter();
        mGetter.setConfiguration(mConfiguration);
    }

    @Test
    public void getCommandOptionsValue() {
        FeatureResponse response = mGetter.execute(FeatureRequest.newBuilder()
                .setName(CommandOptionsGetter.COMMAND_OPTIONS_GETTER)
                .putArgs(CommandOptionsGetter.OPTION_NAME, "filter-previous-passed").build());
        assertEquals("false", response.getResponse());
    }

    @Test
    public void getCommandOptionsValue_updated() throws Exception {
        OptionSetter setter = new OptionSetter(mConfiguration.getCommandOptions());
        setter.setOptionValue("filter-previous-passed", "true");
        FeatureResponse response = mGetter.execute(FeatureRequest.newBuilder()
                .setName(CommandOptionsGetter.COMMAND_OPTIONS_GETTER)
                .putArgs(CommandOptionsGetter.OPTION_NAME, "filter-previous-passed").build());

        assertEquals("true", response.getResponse());
    }
}
