/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.suite.TfSuiteRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.LinkedHashMap;

/**
 * Unit tests for {@link TfSuiteRunner}.
 */
@RunWith(JUnit4.class)
public class TfSuiteRunnerTest {

    private TfSuiteRunner mRunner;

    @Before
    public void setUp() {
        mRunner = new TfSuiteRunner();
    }

    /**
     * Test for {@link TfSuiteRunner#loadTests()} implementation, for basic example configurations.
     */
    @Test
    public void testLoadTests() throws Exception {
        OptionSetter setter = new OptionSetter(mRunner);
        setter.setOptionValue("run-suite-tag", "example-suite");
        LinkedHashMap <String, IConfiguration> configMap = mRunner.loadTests();
        assertEquals(2, configMap.size());
        assertTrue(configMap.containsKey("suite/stub1"));
        assertTrue(configMap.containsKey("suite/stub2"));
    }

    /**
     * Test for {@link TfSuiteRunner#loadTests()} implementation, only stub1.xml is part of this
     * suite.
     */
    @Test
    public void testLoadTests_suite2() throws Exception {
        OptionSetter setter = new OptionSetter(mRunner);
        setter.setOptionValue("run-suite-tag", "example-suite2");
        LinkedHashMap <String, IConfiguration> configMap = mRunner.loadTests();
        assertEquals(1, configMap.size());
        assertTrue(configMap.containsKey("suite/stub1"));
    }

    /**
     * Test that when splitting, the instance of the implementation is used.
     */
    @Test
    public void testSplit() {
        IRemoteTest test = mRunner.getTestShard(1, 0);
        assertTrue(test instanceof TfSuiteRunner);
    }
}
