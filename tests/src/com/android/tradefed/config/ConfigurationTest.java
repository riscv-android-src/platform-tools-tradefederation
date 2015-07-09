/*
 * Copyright (C) 2010 The Android Open Source Project
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
package com.android.tradefed.config;

import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildProvider;
import com.android.tradefed.command.ICommandOptions;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IDeviceRecovery;
import com.android.tradefed.device.IDeviceSelection;
import com.android.tradefed.log.ILeveledLogOutput;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TextResultReporter;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.testtype.IRemoteTest;

import junit.framework.TestCase;

import org.easymock.EasyMock;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for {@link Configuration}.
 */
public class ConfigurationTest extends TestCase {

    private static final String CONFIG_NAME = "name";
    private static final String CONFIG_DESCRIPTION = "config description";
    private static final String CONFIG_OBJECT_TYPE_NAME = "object_name";
    private static final String OPTION_DESCRIPTION = "bool description";
    private static final String OPTION_NAME = "bool";
    private static final String ALT_OPTION_NAME = "map";

    /**
     * Interface for test object stored in a {@link IConfiguration}.
     */
    private static interface TestConfig {

        public boolean getBool();
    }

    private static class TestConfigObject implements TestConfig {

        @Option(name = OPTION_NAME, description = OPTION_DESCRIPTION)
        private boolean mBool;

        @Option(name = ALT_OPTION_NAME, description = OPTION_DESCRIPTION)
        private Map<String, Boolean> mBoolMap = new HashMap<String, Boolean>();

        @Override
        public boolean getBool() {
            return mBool;
        }

        public Map<String, Boolean> getMap() {
            return mBoolMap;
        }
    }

    private Configuration mConfig;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mConfig = new Configuration(CONFIG_NAME, CONFIG_DESCRIPTION);
    }

    /**
     * Test that {@link Configuration#getConfigurationObject(String, Class)} can retrieve
     * a previously stored object.
     */
    public void testGetConfigurationObject() throws ConfigurationException {
        TestConfigObject testConfigObject = new TestConfigObject();
        mConfig.setConfigurationObject(CONFIG_OBJECT_TYPE_NAME, testConfigObject);
        Object fromConfig = mConfig.getConfigurationObject(CONFIG_OBJECT_TYPE_NAME);
        assertEquals(testConfigObject, fromConfig);
    }

    /**
     * Test {@link Configuration#getConfigurationObjectList(String)}
     */
    @SuppressWarnings("unchecked")
    public void testGetConfigurationObjectList() throws ConfigurationException  {
        TestConfigObject testConfigObject = new TestConfigObject();
        mConfig.setConfigurationObject(CONFIG_OBJECT_TYPE_NAME, testConfigObject);
        List<TestConfig> configList = (List<TestConfig>)mConfig.getConfigurationObjectList(
                CONFIG_OBJECT_TYPE_NAME);
        assertEquals(testConfigObject, configList.get(0));
    }

    /**
     * Test that {@link Configuration#getConfigurationObject(String)} with a name that does
     * not exist.
     */
    public void testGetConfigurationObject_wrongname()  {
        assertNull(mConfig.getConfigurationObject("non-existent"));
    }

    /**
     * Test that calling {@link Configuration#getConfigurationObject(String)} for a built-in config
     * type that supports lists.
     */
    public void testGetConfigurationObject_typeIsList()  {
        try {
            mConfig.getConfigurationObject(Configuration.TEST_TYPE_NAME);
            fail("IllegalStateException not thrown");
        } catch (IllegalStateException e) {
            // expected
        }
    }

    /**
     * Test that calling {@link Configuration#getConfigurationObject(String)} for a config type
     * that is a list.
     */
    public void testGetConfigurationObject_forList() throws ConfigurationException  {
        List<TestConfigObject> list = new ArrayList<TestConfigObject>();
        list.add(new TestConfigObject());
        list.add(new TestConfigObject());
        mConfig.setConfigurationObjectList(CONFIG_OBJECT_TYPE_NAME, list);
        try {
            mConfig.getConfigurationObject(CONFIG_OBJECT_TYPE_NAME);
            fail("IllegalStateException not thrown");
        } catch (IllegalStateException e) {
            // expected
        }
    }

    /**
     * Test that setConfigurationObject throws a ConfigurationException when config object provided
     * is not the correct type
     */
    public void testSetConfigurationObject_wrongtype()  {
        try {
            // arbitrarily, use the "Test" type as expected type
            mConfig.setConfigurationObject(Configuration.TEST_TYPE_NAME, new TestConfigObject());
            fail("setConfigurationObject did not throw ConfigurationException");
        } catch (ConfigurationException e) {
            // expected
        }
    }

    /**
     * Test {@link Configuration#getConfigurationObjectList(String, Class)} when config object
     * with given name does not exist.
     */
    public void testGetConfigurationObjectList_wrongname() {
        assertNull(mConfig.getConfigurationObjectList("non-existent"));
    }

    /**
     * Test {@link Configuration#setConfigurationObjectList(String, List)} when config object
     * is the wrong type
     */
    public void testSetConfigurationObjectList_wrongtype() throws ConfigurationException  {
        try {
            List<TestConfigObject> myList = new ArrayList<TestConfigObject>(1);
            myList.add(new TestConfigObject());
            // arbitrarily, use the "Test" type as expected type
            mConfig.setConfigurationObjectList(Configuration.TEST_TYPE_NAME, myList);
            fail("setConfigurationObject did not throw ConfigurationException");
        } catch (ConfigurationException e) {
            // expected
        }
    }

    /**
     * Test method for {@link com.android.tradefed.config.Configuration#getBuildProvider()}.
     */
    public void testGetBuildProvider() throws ConfigurationException, BuildRetrievalError {
        // check that the default provider is present and doesn't blow up
        assertNotNull(mConfig.getBuildProvider().getBuild());
        // check set and get
        final IBuildProvider provider = EasyMock.createMock(IBuildProvider.class);
        mConfig.setBuildProvider(provider);
        assertEquals(provider, mConfig.getBuildProvider());
    }

    /**
     * Test method for {@link Configuration#getTargetPreparer()}.
     */
    public void testGetTargetPreparers() throws Exception {
        // check that the default preparer is present and doesn't blow up
        mConfig.getTargetPreparers().get(0).setUp(null, null);
        // test set and get
        final ITargetPreparer prep = EasyMock.createMock(ITargetPreparer.class);
        mConfig.setTargetPreparer(prep);
        assertEquals(prep, mConfig.getTargetPreparers().get(0));
    }

    /**
     * Test method for {@link Configuration#getTests()}.
     */
    public void testGetTests() throws ConfigurationException, DeviceNotAvailableException {
        // check that the default test is present and doesn't blow up
        mConfig.getTests().get(0).run(new TextResultReporter());
        IRemoteTest test1 = EasyMock.createMock(IRemoteTest.class);
        mConfig.setTest(test1);
        assertEquals(test1, mConfig.getTests().get(0));
    }

    /**
     * Test method for {@link Configuration#getDeviceRecovery()}.
     */
    public void testGetDeviceRecovery() throws ConfigurationException {
        // check that the default recovery is present
        assertNotNull(mConfig.getDeviceRecovery());
        final IDeviceRecovery recovery = EasyMock.createMock(IDeviceRecovery.class);
        mConfig.setDeviceRecovery(recovery);
        assertEquals(recovery, mConfig.getDeviceRecovery());
    }

    /**
     * Test method for {@link Configuration#getLogOutput()}.
     */
    public void testGetLogOutput() throws ConfigurationException {
        // check that the default logger is present and doesn't blow up
        mConfig.getLogOutput().printLog(LogLevel.INFO, "testGetLogOutput", "test");
        final ILeveledLogOutput logger = EasyMock.createMock(ILeveledLogOutput.class);
        mConfig.setLogOutput(logger);
        assertEquals(logger, mConfig.getLogOutput());
    }

    /**
     * Test method for {@link Configuration#getTestInvocationListeners()}.
     * @throws ConfigurationException
     */
    public void testGetTestInvocationListeners() throws ConfigurationException {
        // check that the default listener is present and doesn't blow up
        ITestInvocationListener defaultListener = mConfig.getTestInvocationListeners().get(0);
        defaultListener.invocationStarted(new BuildInfo());
        defaultListener.invocationEnded(1);

        final ITestInvocationListener listener1 = EasyMock.createMock(
                ITestInvocationListener.class);
        mConfig.setTestInvocationListener(listener1);
        assertEquals(listener1, mConfig.getTestInvocationListeners().get(0));
    }

    /**
     * Test method for {@link Configuration#getCommandOptions()}.
     */
    public void testGetCommandOptions() throws ConfigurationException {
        // check that the default object is present
        assertNotNull(mConfig.getCommandOptions());
        final ICommandOptions cmdOptions = EasyMock.createMock(ICommandOptions.class);
        mConfig.setCommandOptions(cmdOptions);
        assertEquals(cmdOptions, mConfig.getCommandOptions());
    }

    /**
     * Test method for {@link Configuration#getDeviceRequirements()}.
     */
    public void testGetDeviceRequirements() throws ConfigurationException {
        // check that the default object is present
        assertNotNull(mConfig.getDeviceRequirements());
        final IDeviceSelection deviceSelection = EasyMock.createMock(
                IDeviceSelection.class);
        mConfig.setDeviceRequirements(deviceSelection);
        assertEquals(deviceSelection, mConfig.getDeviceRequirements());
    }

    /**
     * Test {@link Configuration#setConfigurationObject(String, Object)} with a
     * {@link IConfigurationReceiver}
     */
    public void testSetConfigurationObject_configReceiver() throws ConfigurationException {
        final IConfigurationReceiver mockConfigReceiver = EasyMock.createMock(
                IConfigurationReceiver.class);
        mockConfigReceiver.setConfiguration(mConfig);
        EasyMock.replay(mockConfigReceiver);
        mConfig.setConfigurationObject("example", mockConfigReceiver);
        EasyMock.verify(mockConfigReceiver);
    }

    /**
     * Test {@link Configuration#injectOptionValue(String, String)}
     */
    public void testInjectOptionValue() throws ConfigurationException {
        TestConfigObject testConfigObject = new TestConfigObject();
        mConfig.setConfigurationObject(CONFIG_OBJECT_TYPE_NAME, testConfigObject);
        mConfig.injectOptionValue(OPTION_NAME, Boolean.toString(true));
        assertTrue(testConfigObject.getBool());
    }

    /**
     * Test {@link Configuration#injectOptionValue(String, String, String)}
     */
    public void testInjectMapOptionValue() throws ConfigurationException {
        final String key = "hello";

        TestConfigObject testConfigObject = new TestConfigObject();
        mConfig.setConfigurationObject(CONFIG_OBJECT_TYPE_NAME, testConfigObject);
        assertEquals(0, testConfigObject.getMap().size());
        mConfig.injectOptionValue(ALT_OPTION_NAME, key, Boolean.toString(true));

        Map<String, Boolean> map = testConfigObject.getMap();
        assertEquals(1, map.size());
        assertNotNull(map.get(key));
        assertTrue(map.get(key).booleanValue());
    }

    /**
     * Basic test for {@link Configuration#printCommandUsage(java.io.PrintStream)}.
     */
    public void testPrintCommandUsage() throws ConfigurationException {
        TestConfigObject testConfigObject = new TestConfigObject();
        mConfig.setConfigurationObject(CONFIG_OBJECT_TYPE_NAME, testConfigObject);
        // dump the print stream results to the ByteArrayOutputStream, so contents can be evaluated
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream mockPrintStream = new PrintStream(outputStream);
        mConfig.printCommandUsage(false, mockPrintStream);

        // verifying exact contents would be prone to high-maintenance, so instead, just validate
        // all expected names are present
        final String usageString = outputStream.toString();
        assertTrue("Usage text does not contain config name", usageString.contains(CONFIG_NAME));
        assertTrue("Usage text does not contain config description", usageString.contains(
                CONFIG_DESCRIPTION));
        assertTrue("Usage text does not contain object name", usageString.contains(
                CONFIG_OBJECT_TYPE_NAME));
        assertTrue("Usage text does not contain option name", usageString.contains(OPTION_NAME));
        assertTrue("Usage text does not contain option description",
                usageString.contains(OPTION_DESCRIPTION));

        // ensure help prints out options from default config types
        assertTrue("Usage text does not contain --serial option name",
                usageString.contains("serial"));

    }

    /**
     * Basic test for {@link Configuration#getJsonCommandUsage()}.
     */
    public void testGetJsonCommandUsage() throws ConfigurationException, JSONException {
        TestConfigObject testConfigObject = new TestConfigObject();
        mConfig.setConfigurationObject(CONFIG_OBJECT_TYPE_NAME, testConfigObject);

        // General validation of usage elements
        JSONArray usage = mConfig.getJsonCommandUsage();
        JSONObject jsonConfigObject = null;
        for (int i = 0; i < usage.length(); i++) {
            JSONObject optionClass = usage.getJSONObject(i);

            // Each element should contain 'name', 'class', and 'fields' values
            assertTrue("Usage element does not contain a 'name' value", optionClass.has("name"));
            assertTrue("Usage element does not contain a 'class' value", optionClass.has("class"));
            assertTrue("Usage element does not contain a 'fields' value",
                    optionClass.has("fields"));

            // General validation of each field
            JSONArray fields = optionClass.getJSONArray("fields");
            for (int j = 0; j < fields.length(); j++) {
                JSONObject field = fields.getJSONObject(j);

                // Each field should at least have 'name', 'description', 'mandatory',
                // 'javaClass', and 'updateRule' values
                assertTrue("Option field does not have a 'name' value", field.has("name"));
                assertTrue("Option field does not have a 'description' value",
                        field.has("description"));
                assertTrue("Option field does not have a 'mandatory' value",
                        field.has("mandatory"));
                assertTrue("Option field does not have a 'javaClass' value",
                        field.has("javaClass"));
                assertTrue("Option field does not have an 'updateRule' value",
                        field.has("updateRule"));
            }

            // The only elements should either be built-in types, or the configuration object we
            // added.
            String name = optionClass.getString("name");
            if (name.equals(CONFIG_OBJECT_TYPE_NAME)) {
                // The object we added should only appear once
                assertNull("Duplicate JSON usage element", jsonConfigObject);
                jsonConfigObject = optionClass;
            } else {
                assertTrue(String.format("Unexpected JSON usage element: %s", name),
                    Configuration.isBuiltInObjType(name));
            }
        }

        // Verify that the configuration element we added has the expected values
        assertNotNull("Missing JSON usage element", jsonConfigObject);
        JSONArray fields = jsonConfigObject.getJSONArray("fields");
        JSONObject jsonOptionField = null;
        JSONObject jsonAltOptionField = null;
        for (int i = 0; i < fields.length(); i++) {
            JSONObject field = fields.getJSONObject(i);

            if (OPTION_NAME.equals(field.getString("name"))) {
                assertNull("Duplicate option field", jsonOptionField);
                jsonOptionField = field;
            } else if (ALT_OPTION_NAME.equals(field.getString("name"))) {
                assertNull("Duplication option field", jsonAltOptionField);
                jsonAltOptionField = field;
            }
        }
        assertNotNull(jsonOptionField);
        assertEquals(OPTION_DESCRIPTION, jsonOptionField.getString("description"));
        assertNotNull(jsonAltOptionField);
        assertEquals(OPTION_DESCRIPTION, jsonAltOptionField.getString("description"));
    }
}
