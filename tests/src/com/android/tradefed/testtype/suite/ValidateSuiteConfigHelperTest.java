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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.LocalFolderBuildProvider;
import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.DeviceConfigurationHolder;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IDeviceConfiguration;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.metric.BaseDeviceMetricCollector;
import com.android.tradefed.device.metric.IMetricCollector;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TextResultReporter;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.StubTargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.targetprep.multi.IMultiTargetPreparer;
import com.android.tradefed.targetprep.multi.StubMultiTargetPreparer;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;

/** Unit tests for {@link ValidateSuiteConfigHelper} */
@RunWith(JUnit4.class)
public class ValidateSuiteConfigHelperTest {

    /** Test that a config with default value can run as suite. */
    @Test
    public void testCanRunAsSuite() {
        IConfiguration config = new Configuration("test", "test description");
        assertTrue(ValidateSuiteConfigHelper.validateConfig(config));
    }

    /** Test that a config with a build provider cannot run as suite. */
    @Test
    public void testNotRunningAsSuite_buildProvider() {
        IConfiguration config = new Configuration("test", "test description");
        // LocalFolderBuildProvider extends the default StubBuildProvider but is still correctly
        // rejected.
        config.setBuildProvider(new LocalFolderBuildProvider());
        assertFalse(ValidateSuiteConfigHelper.validateConfig(config));
    }

    /**
     * Test that a config using the device holder config (multi device) is correctly rejected since
     * it is not using the default build_provider.
     */
    @Test
    public void testNotRunningAsSuite_MultiDevice_buildProvider() throws Exception {
        IConfiguration config = new Configuration("test", "test description");
        // LocalFolderBuildProvider extends the default StubBuildProvider but is still correctly
        // rejected.
        IDeviceConfiguration deviceConfig = new DeviceConfigurationHolder("default");
        deviceConfig.addSpecificConfig(new LocalFolderBuildProvider());
        config.setDeviceConfig(deviceConfig);
        assertFalse(ValidateSuiteConfigHelper.validateConfig(config));
    }

    /** Test that a config with a result reporter cannot run as suite. */
    @Test
    public void testNotRunningAsSuite_resultReporter() {
        IConfiguration config = new Configuration("test", "test description");
        config.setTestInvocationListener(new CollectingTestListener());
        assertFalse(ValidateSuiteConfigHelper.validateConfig(config));
    }

    /** Test that a config with a multiple result reporter cannot run as suite. */
    @Test
    public void testNotRunningAsSuite_multi_resultReporter() {
        IConfiguration config = new Configuration("test", "test description");
        List<ITestInvocationListener> listeners = new ArrayList<>();
        listeners.add(new TextResultReporter());
        listeners.add(new CollectingTestListener());
        config.setTestInvocationListeners(listeners);
        assertFalse(ValidateSuiteConfigHelper.validateConfig(config));
    }

    /** Test that a config that contains simple target preparers is allows to run in a suite. */
    @Test
    public void testTargetPrep() {
        IConfiguration config = new Configuration("test", "test description");
        config.setTargetPreparer(new StubTargetPreparer());
        config.setMultiTargetPreparer(new StubMultiTargetPreparer());
        assertTrue(ValidateSuiteConfigHelper.validateConfig(config));
    }

    /**
     * Test that a config that contains a target preparer that implements both single and multi
     * target preparer. It should be rejected.
     */
    @Test
    public void testTargetPrep_badPreparer() {
        IConfiguration config = new Configuration("test", "test description");
        config.setTargetPreparer(new BadSingleMultiPreparer());
        assertFalse(ValidateSuiteConfigHelper.validateConfig(config));
    }

    /**
     * Test that a config that contains a multi target preparer that implements both single and
     * multi target preparer. It should be rejected.
     */
    @Test
    public void testTargetPrep_badMultiPreparer() {
        IConfiguration config = new Configuration("test", "test description");
        config.setMultiTargetPreparer(new BadSingleMultiPreparer());
        assertFalse(ValidateSuiteConfigHelper.validateConfig(config));
    }

    /**
     * Test class of a badly implemented preparer that extends both concepts: multiple and single
     * devices.
     */
    public static class BadSingleMultiPreparer implements ITargetPreparer, IMultiTargetPreparer {
        @Override
        public void setUp(IInvocationContext context)
                throws TargetSetupError, BuildError, DeviceNotAvailableException {
            // ignore
        }

        @Override
        public void setUp(ITestDevice device, IBuildInfo buildInfo)
                throws TargetSetupError, BuildError, DeviceNotAvailableException {
            // ignore
        }

        @Override
        public void setDisable(boolean isDisabled) {
            // ignore
        }
    }

    /** Test that metric collectors cannot be specified inside a module for a suite. */
    @Test
    public void testMetricCollectors() {
        IConfiguration config = new Configuration("test", "test description");
        List<IMetricCollector> collectors = new ArrayList<>();
        collectors.add(new BaseDeviceMetricCollector());
        config.setDeviceMetricCollectors(collectors);
        assertFalse(ValidateSuiteConfigHelper.validateConfig(config));
    }
}
