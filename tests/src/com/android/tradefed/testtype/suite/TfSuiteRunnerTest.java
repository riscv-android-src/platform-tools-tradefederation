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
import static org.junit.Assert.fail;

import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.StubTest;

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

    /**
     * Attempt to load a suite from a suite, but the sub-suite does not have a default run-suite-tag
     * so it cannot run anything.
     */
    @Test
    public void testLoadSuite_noSubConfigs() throws ConfigurationException {
        OptionSetter setter = new OptionSetter(mRunner);
        setter.setOptionValue("run-suite-tag", "test-empty");
        LinkedHashMap<String, IConfiguration> configMap = mRunner.loadTests();
        assertEquals(0, configMap.size());
    }

    /**
     * Attempt to load a suite from a suite, the sub-suite has a default run-suite-tag that will be
     * loaded.
     */
    @Test
    public void testLoadSuite() throws ConfigurationException {
        OptionSetter setter = new OptionSetter(mRunner);
        setter.setOptionValue("run-suite-tag", "test-sub-suite");
        LinkedHashMap<String, IConfiguration> configMap = mRunner.loadTests();
        assertEquals(3, configMap.size());
        // 2 test configs loaded from the sub-suite
        assertTrue(configMap.containsKey("suite/stub1"));
        assertTrue(configMap.containsKey("suite/stub2"));
        // 1 config from the left over <test> that was not a suite.
        assertTrue(configMap.containsKey("suite/sub-suite"));
        IConfiguration config = configMap.get("suite/sub-suite");
        // assert that the TfSuiteRunner was removed from the config, only the stubTest remains
        assertTrue(config.getTests().size() == 1);
        assertTrue(config.getTests().get(0) instanceof StubTest);
    }

    /**
     * In case of cycle include of sub-suite configuration. We throw an exception to prevent any
     * weird runs.
     */
    @Test
    public void testLoadSuite_cycle() throws ConfigurationException {
        OptionSetter setter = new OptionSetter(mRunner);
        setter.setOptionValue("run-suite-tag", "test-cycle-a");
        try {
            mRunner.loadTests();
            fail("Should have thrown an exception.");
        } catch (RuntimeException expected) {
            // expected
        }
    }
}
