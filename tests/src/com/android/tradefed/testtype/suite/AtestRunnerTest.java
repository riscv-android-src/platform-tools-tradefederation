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

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TestAppInstallSetup;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.IConfigurationFactory;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.testtype.InstrumentationTest;
import com.android.tradefed.testtype.ITestFilterReceiver;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashMap;

import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link TfSuiteRunner}. */
@RunWith(JUnit4.class)
public class AtestRunnerTest {

    private AtestRunner mSpyRunner;
    private OptionSetter setter;
    private File mTmpFile;
    private String mInfoTemplate = "{\"test\": \"%s\", \"filters\": [%s]}";
    private String mFilterTemplate = "\"%s\"";
    private String module1 = "module1";
    private String module2 = "module2";
    private String classA = "fully.qualified.classA";
    private String classB = "fully.qualified.classB";
    private String method1 = "method1";
    private List<String> mConfigList = Arrays.asList(module1, module2);
    private HashMap<String, String> mInfos = new HashMap<>();

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

    private IConfiguration createFakeConfig() {
        IConfiguration fakeConfig = new Configuration("fake_name", "fake_desc");
        IRemoteTest fakeTest = new FakeTest();
        fakeConfig.setTests(Arrays.asList(fakeTest));
        ITargetPreparer targetPreparer = new TestAppInstallSetup();
        fakeConfig.setTargetPreparer(targetPreparer);
        return fakeConfig;
    }

    public AtestRunnerTest() throws Exception {
        mInfos.put("module1", "[" + String.format(mInfoTemplate, module1, "") + "]");
        mInfos.put(
                "module1_module2",
                "["
                        + String.format(mInfoTemplate, module1, "")
                        + ","
                        + String.format(mInfoTemplate, module2, "")
                        + "]");
        mInfos.put(
                "module1_class_class",
                "["
                        + String.format(
                                mInfoTemplate,
                                module1,
                                String.join(
                                        ",",
                                        String.format(mFilterTemplate, classA),
                                        String.format(mFilterTemplate, classB)))
                        + "]");
        mInfos.put(
                "module1_classA_method_classB_method",
                "["
                        + String.format(
                                mInfoTemplate,
                                module1,
                                String.join(
                                        ",",
                                        String.format(mFilterTemplate, classA + "#" + method1),
                                        String.format(mFilterTemplate, classB + "#" + method1)))
                        + "]");
        mInfos.put(
                "module1_classA_module2_classB_method",
                "["
                        + String.format(
                                mInfoTemplate, module1, String.format(mFilterTemplate, classA))
                        + ","
                        + String.format(
                                mInfoTemplate,
                                module2,
                                String.format(mFilterTemplate, classB + "#" + method1))
                        + "]");
    }

    @Before
    public void setUp() throws Exception {
        mTmpFile = File.createTempFile("atest-", "-unit-test-file.json");
        IConfigurationFactory mockedConfigFactory = mock(IConfigurationFactory.class);
        when(mockedConfigFactory.getConfigList(null, false)).thenReturn(mConfigList);
        // Return a new config for each call.
        when(mockedConfigFactory.createConfigurationFromArgs(any()))
                .thenReturn(createFakeConfig(), createFakeConfig());
        mSpyRunner = spy(new AtestRunner());
        when(mSpyRunner.loadConfigFactory()).thenReturn(mockedConfigFactory);
    }

    @After
    public void tearDown() throws Exception {
        mTmpFile.delete();
    }

    private String truncateAndWrite(File f, String body) throws Exception {
        List<String> lines = Arrays.asList(body.split("\n"));
        Files.write(f.toPath(), lines, StandardOpenOption.TRUNCATE_EXISTING);
        return f.toString();
    }

    @Test
    public void testLoadTestInfoFile() throws Exception {
        String filePath =
                truncateAndWrite(mTmpFile, mInfos.get("module1_classA_method_classB_method"));
        AtestRunner.TestInfo[] infos = mSpyRunner.loadTestInfoFile(filePath);
        assertEquals(infos[0].test, module1);
        assertEquals(infos[0].filters[0], classA + "#" + method1);
        assertEquals(infos[0].filters[1], classB + "#" + method1);
    }

    /** Tests for {@link AtestRunner#loadTests()} implementation. */
    @Test(expected = Exception.class)
    public void testLoadTests_none() throws Exception {
        String filePath = truncateAndWrite(mTmpFile, "");
        setter = new OptionSetter(mSpyRunner);
        setter.setOptionValue("test-info-file", filePath);
        LinkedHashMap<String, IConfiguration> configMap = mSpyRunner.loadTests();
    }

    @Test
    public void testLoadTests_one() throws Exception {
        String filePath = truncateAndWrite(mTmpFile, mInfos.get("module1"));
        setter = new OptionSetter(mSpyRunner);
        setter.setOptionValue("test-info-file", filePath);
        LinkedHashMap<String, IConfiguration> configMap = mSpyRunner.loadTests();
        assertEquals(1, configMap.size());
        assertTrue(configMap.containsKey("module1"));
    }

    @Test
    public void testLoadTests_two() throws Exception {
        String filePath = truncateAndWrite(mTmpFile, mInfos.get("module1_module2"));
        setter = new OptionSetter(mSpyRunner);
        setter.setOptionValue("test-info-file", filePath);
        LinkedHashMap<String, IConfiguration> configMap = mSpyRunner.loadTests();
        assertEquals(2, configMap.size());
        assertTrue(configMap.containsKey("module1"));
        assertTrue(configMap.containsKey("module2"));
    }

    @Test
    public void testLoadTests_class() throws Exception {
        String filePath = truncateAndWrite(mTmpFile, mInfos.get("module1_class_class"));
        setter = new OptionSetter(mSpyRunner);
        setter.setOptionValue("test-info-file", filePath);
        LinkedHashMap<String, IConfiguration> configMap = mSpyRunner.loadTests();
        assertEquals(1, configMap.size());
        assertTrue(configMap.containsKey("module1"));
        IConfiguration config = configMap.get("module1");
        List<IRemoteTest> tests = config.getTests();
        assertEquals(1, tests.size());
        IRemoteTest test = tests.get(0);
        assertEquals(((FakeTest) test).mIncludeFilters, Arrays.asList(classA, classB));
    }

    @Test
    public void testLoadTests_method() throws Exception {
        String filePath =
                truncateAndWrite(mTmpFile, mInfos.get("module1_classA_method_classB_method"));
        setter = new OptionSetter(mSpyRunner);
        setter.setOptionValue("test-info-file", filePath);
        LinkedHashMap<String, IConfiguration> configMap = mSpyRunner.loadTests();
        assertEquals(1, configMap.size());
        assertTrue(configMap.containsKey("module1"));
        IConfiguration config = configMap.get("module1");
        List<IRemoteTest> tests = config.getTests();
        assertEquals(1, tests.size());
        IRemoteTest test = tests.get(0);
        assertEquals(
                ((FakeTest) test).mIncludeFilters,
                Arrays.asList(classA + "#" + method1, classB + "#" + method1));
    }

    @Test
    public void testLoadTests_multiple() throws Exception {
        String filePath =
                truncateAndWrite(mTmpFile, mInfos.get("module1_classA_module2_classB_method"));
        setter = new OptionSetter(mSpyRunner);
        setter.setOptionValue("test-info-file", filePath);
        LinkedHashMap<String, IConfiguration> configMap = mSpyRunner.loadTests();
        assertEquals(2, configMap.size());
        assertTrue(configMap.containsKey("module1"));
        assertTrue(configMap.containsKey("module2"));
        IConfiguration config = configMap.get("module1");
        List<IRemoteTest> tests = config.getTests();
        assertEquals(1, tests.size());
        IRemoteTest test = tests.get(0);
        assertEquals(Arrays.asList(classA), ((FakeTest) test).mIncludeFilters);
        IConfiguration config2 = configMap.get("module2");
        List<IRemoteTest> tests2 = config2.getTests();
        assertEquals(1, tests2.size());
        IRemoteTest test2 = tests2.get(0);
        assertEquals(((FakeTest) test2).mIncludeFilters, Arrays.asList(classB + "#" + method1));
    }

    @Test
    public void testWaitForDebugger() throws Exception {
        String filePath = truncateAndWrite(mTmpFile, mInfos.get("module1"));
        OptionSetter setter = new OptionSetter(mSpyRunner);
        setter.setOptionValue("wait-for-debugger", "true");
        setter.setOptionValue("test-info-file", filePath);
        LinkedHashMap<String, IConfiguration> configMap = mSpyRunner.loadTests();
        assertEquals(1, configMap.size());
        IConfiguration config = configMap.get("module1");
        IRemoteTest test = config.getTests().get(0);
        assertTrue(((FakeTest) test).getDebug());
    }

    @Test
    public void testdisableTargetPreparers() throws Exception {
        String filePath = truncateAndWrite(mTmpFile, mInfos.get("module1"));
        OptionSetter setter = new OptionSetter(mSpyRunner);
        setter.setOptionValue("disable-target-preparers", "true");
        setter.setOptionValue("test-info-file", filePath);
        LinkedHashMap<String, IConfiguration> configMap = mSpyRunner.loadTests();
        assertEquals(1, configMap.size());
        IConfiguration config = configMap.get("module1");
        for (ITargetPreparer targetPreparer : config.getTargetPreparers()) {
            assertTrue(targetPreparer.isDisabled());
        }
    }

    @Test
    public void testdisableTargetPreparersUnset() throws Exception {
        String filePath = truncateAndWrite(mTmpFile, mInfos.get("module1"));
        OptionSetter setter = new OptionSetter(mSpyRunner);
        setter.setOptionValue("test-info-file", filePath);
        LinkedHashMap<String, IConfiguration> configMap = mSpyRunner.loadTests();
        assertEquals(1, configMap.size());
        IConfiguration config = configMap.get("module1");
        for (ITargetPreparer targetPreparer : config.getTargetPreparers()) {
            assertTrue(!targetPreparer.isDisabled());
        }
    }

    @Test
    public void testDisableTearDown() throws Exception {
        String filePath = truncateAndWrite(mTmpFile, mInfos.get("module1"));
        OptionSetter setter = new OptionSetter(mSpyRunner);
        setter.setOptionValue("disable-teardown", "true");
        setter.setOptionValue("test-info-file", filePath);
        LinkedHashMap<String, IConfiguration> configMap = mSpyRunner.loadTests();
        assertEquals(1, configMap.size());
        IConfiguration config = configMap.get("module1");
        for (ITargetPreparer targetPreparer : config.getTargetPreparers()) {
            assertTrue(targetPreparer.isTearDownDisabled());
        }
    }

    @Test
    public void testDisableTearDownUnset() throws Exception {
        String filePath = truncateAndWrite(mTmpFile, mInfos.get("module1"));
        OptionSetter setter = new OptionSetter(mSpyRunner);
        setter.setOptionValue("test-info-file", filePath);
        LinkedHashMap<String, IConfiguration> configMap = mSpyRunner.loadTests();
        assertEquals(1, configMap.size());
        IConfiguration config = configMap.get("module1");
        for (ITargetPreparer targetPreparer : config.getTargetPreparers()) {
            assertTrue(!targetPreparer.isTearDownDisabled());
        }
    }
}
