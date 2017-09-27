/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationFactory;
import com.android.tradefed.config.OptionSetter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link TfSuiteRunner}. */
@RunWith(JUnit4.class)
public class AtestRunnerTest {

    private AtestRunner mSpyRunner;
    private IConfigurationFactory mMockedConfigFactory;
    private List<String> mConfigList = Arrays.asList("Module1", "Module2");

    @Before
    public void setUp() {
        mMockedConfigFactory = mock(IConfigurationFactory.class);
        when(mMockedConfigFactory.getConfigList(null, false)).thenReturn(mConfigList);
        mSpyRunner = spy(new AtestRunner());
        when(mSpyRunner.loadConfigFactory()).thenReturn(mMockedConfigFactory);
    }

    /** Tests for {@link AtestRunner#loadTests()} implementation. */
    @Test
    public void testLoadTests_none() throws Exception {
        LinkedHashMap<String, IConfiguration> configMap = mSpyRunner.loadTests();
        assertEquals(0, configMap.size());
    }

    @Test
    public void testLoadTests_one() throws Exception {
        OptionSetter setter = new OptionSetter(mSpyRunner);
        setter.setOptionValue("test-info", "Module1");
        LinkedHashMap<String, IConfiguration> configMap = mSpyRunner.loadTests();
        assertEquals(1, configMap.size());
        assertTrue(configMap.containsKey("Module1"));
    }

    @Test
    public void testLoadTests_two() throws Exception {
        OptionSetter setter = new OptionSetter(mSpyRunner);
        setter.setOptionValue("test-info", "Module1");
        setter.setOptionValue("test-info", "Module2");
        setter.setOptionValue("test-info", "Module3");
        LinkedHashMap<String, IConfiguration> configMap = mSpyRunner.loadTests();
        assertEquals(2, configMap.size());
        assertTrue(configMap.containsKey("Module1"));
        assertTrue(configMap.containsKey("Module2"));
    }
}
