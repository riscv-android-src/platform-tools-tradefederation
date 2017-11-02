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
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import com.android.tradefed.command.ICommandOptions;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.IConfigurationFactory;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.InstrumentationTest;
import com.android.tradefed.testtype.ITestFilterReceiver;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashMap;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link TfSuiteRunner}. */
@RunWith(JUnit4.class)
public class AtestRunnerTest {

    private AtestRunner mSpyRunner;
    private IConfigurationFactory mMockedConfigFactory;
    private OptionSetter setter;
    private String module1 = "module1";
    private String module2 = "module2";
    private String classA = "fully.qualified.classA";
    private String classB = "fully.qualified.classB";
    private List<String> mConfigList = Arrays.asList(module1, module2);
    private HashMap<String, String> params = new HashMap<>();
    private IConfiguration mFakeConfig = new Configuration("fake_name", "fake_desc");

    private class FakeTest extends InstrumentationTest implements ITestFilterReceiver, IRemoteTest {

        private List<String> mIncludeFilters = new ArrayList<>();

        /** {@inheritDoc} */
        @Override
        public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
            // Must implement as part of IRemoteTest
        }

        /** {@inheritDoc} */
        @Override
        public void addIncludeFilter(String filter) {
            mIncludeFilters.add(filter);
        }

        /** {@inheritDoc} */
        @Override
        public void addAllIncludeFilters(Set<String> filters) {
            mIncludeFilters.addAll(filters);
        }

        /** {@inheritDoc} */
        @Override
        public void addExcludeFilter(String filter) {
            // Must implement as part of ITestFilterReceiver
        }

        /** {@inheritDoc} */
        @Override
        public void addAllExcludeFilters(Set<String> filters) {
            // Must implement as part of ITestFilterReceiver
        }
    }

    private IRemoteTest mFakeTest = new FakeTest();

    public AtestRunnerTest() {
        mFakeConfig.setTests(Arrays.asList(mFakeTest));
        params.put("module1", module1);
        params.put("module2", module2);
        params.put("module1_class", String.format("%s:%s", module1, classA));
        params.put("module2_class", String.format("%s:%s", module2, classA));
        params.put("module1_class_class", String.format("%s:%s,%s", module1, classA, classB));
        params.put("module2_class_class", String.format("%s:%s,%s", module2, classA, classB));
    }

    @Before
    public void setUp() throws Exception {
        mMockedConfigFactory = mock(IConfigurationFactory.class);
        when(mMockedConfigFactory.getConfigList(null, false)).thenReturn(mConfigList);
        when(mMockedConfigFactory.createConfigurationFromArgs(any())).thenReturn(mFakeConfig);
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
        setter = new OptionSetter(mSpyRunner);
        setter.setOptionValue("test-info", params.get("module1"));
        LinkedHashMap<String, IConfiguration> configMap = mSpyRunner.loadTests();
        assertEquals(1, configMap.size());
        assertTrue(configMap.containsKey("module1"));
    }

    @Test
    public void testLoadTests_two() throws Exception {
        setter = new OptionSetter(mSpyRunner);
        setter.setOptionValue("test-info", params.get("module1"));
        setter.setOptionValue("test-info", params.get("module2"));
        setter.setOptionValue("test-info", "not_a_module");
        LinkedHashMap<String, IConfiguration> configMap = mSpyRunner.loadTests();
        assertEquals(2, configMap.size());
        assertTrue(configMap.containsKey("module1"));
        assertTrue(configMap.containsKey("module2"));
    }

    @Test
    public void testLoadTests_class() throws Exception {
        setter = new OptionSetter(mSpyRunner);
        setter.setOptionValue("test-info", params.get("module1_class_class"));
        LinkedHashMap<String, IConfiguration> configMap = mSpyRunner.loadTests();
        assertEquals(1, configMap.size());
        assertTrue(configMap.containsKey("module1"));
        IConfiguration config = configMap.get("module1");
        List<IRemoteTest> tests = config.getTests();
        assertEquals(1, tests.size());
        IRemoteTest test = tests.get(0);
        assertEquals(test, mFakeTest);
        assertEquals(((FakeTest) test).mIncludeFilters, Arrays.asList(classA, classB));
    }

    @Test
    public void testparseTestInfoParam() throws Exception {
        HashMap<String, List<String>> result = mSpyRunner.parseTestInfoParam(params.get("module1"));
        assertTrue(result.containsKey("name"));
        assertTrue(result.containsKey("filters"));
        assertEquals(Arrays.asList(module1), result.get("name"));
        assertEquals(0, result.get("filters").size());
        HashMap<String, List<String>> result2 =
                mSpyRunner.parseTestInfoParam(params.get("module1_class"));
        assertTrue(result2.containsKey("name"));
        assertTrue(result2.containsKey("filters"));
        assertEquals(Arrays.asList(module1), result2.get("name"));
        assertEquals(Arrays.asList(classA), result2.get("filters"));
        HashMap<String, List<String>> result3 =
                mSpyRunner.parseTestInfoParam(params.get("module1_class_class"));
        assertTrue(result3.containsKey("name"));
        assertTrue(result3.containsKey("filters"));
        assertEquals(Arrays.asList(module1), result3.get("name"));
        assertEquals(Arrays.asList(classA, classB), result3.get("filters"));
    }

    @Test
    public void testWaitForDebugger() throws Exception {
        OptionSetter setter = new OptionSetter(mSpyRunner);
        setter.setOptionValue("wait-for-debugger", "true");
        setter.setOptionValue("test-info", params.get("module1"));
        LinkedHashMap<String, IConfiguration> configMap = mSpyRunner.loadTests();
        assertEquals(1, configMap.size());
        IConfiguration config = configMap.get("module1");
        ICommandOptions options = config.getCommandOptions();
        CLog.e("options: %s", options.getInvocationData().get("debug"));
        assertTrue(((FakeTest) mFakeTest).getDebug());
    }
}



