/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tradefed.testtype.junit4;

import android.longevity.core.LongevitySuite;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.testtype.HostTest;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IInvocationContextReceiver;
import com.android.tradefed.testtype.IMultiDeviceTest;
import com.android.tradefed.testtype.junit4.builder.DeviceJUnit4ClassRunnerBuilder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.InitializationError;

/**
 * A JUnit4-based {@link Runner} that composes tests run with {@link DeviceJUnit4ClassRunner} into a
 * {@link LongevitySuite}, which runs tests repeatedly to induce stress and randomness. Tests should
 * specify this inside a @RunWith annotation with a list of {@link SuiteClasses} to include.
 *
 * <p>
 *
 * @see LongevitySuite
 */
public class LongevityHostRunner extends Runner
        implements IDeviceTest,
                IBuildReceiver,
                IAbiReceiver,
                IMultiDeviceTest,
                IInvocationContextReceiver {
    private ITestDevice mDevice;
    private IBuildInfo mBuildInfo;
    private IAbi mAbi;
    private IInvocationContext mContext;
    private Map<ITestDevice, IBuildInfo> mDeviceInfos;

    @Option(name = HostTest.SET_OPTION_NAME, description = HostTest.SET_OPTION_DESC)
    private Set<String> mKeyValueOptions = new HashSet<>();

    @Option(
        name = "iterations",
        description = "The number of times to repeat the tests in this suite."
    )
    private int mIterations = 1;

    private Class<?> mSuiteKlass;

    public LongevityHostRunner(Class<?> klass) {
        mSuiteKlass = klass;
    }

    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    @Override
    public void setAbi(IAbi abi) {
        mAbi = abi;
    }

    @Override
    public IAbi getAbi() {
        return mAbi;
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }

    @Override
    public void setInvocationContext(IInvocationContext invocationContext) {
        mContext = invocationContext;
    }

    @Override
    public void setDeviceInfos(Map<ITestDevice, IBuildInfo> deviceInfos) {
        mDeviceInfos = deviceInfos;
    }

    /** Constructs an underlying {@link LongevitySuite} using options from this {@link Runner}. */
    private LongevitySuite constructSuite() {
        // Construct runner option map from TF options.
        Map<String, String> options = new HashMap<>();
        options.put("iterations", String.valueOf(mIterations));
        // Create the suite and return the suite, verifying construction.
        try {
            return new LongevitySuite(mSuiteKlass, new DeviceJUnit4ClassRunnerBuilder(), options);
        } catch (InitializationError e) {
            throw new RuntimeException("Unable to construct longevity suite", e);
        }
    }

    @Override
    public Description getDescription() {
        return constructSuite().getDescription();
    }

    @Override
    public void run(RunNotifier notifier) {
        // Create and verify the suite.
        LongevitySuite suite = constructSuite();
        // Pass the feature set to the runners.
        for (Runner child : suite.getRunners()) {
            if (child instanceof IDeviceTest) {
                ((IDeviceTest) child).setDevice(mDevice);
            }
            if (child instanceof IAbiReceiver) {
                ((IAbiReceiver) child).setAbi(mAbi);
            }
            if (child instanceof IBuildReceiver) {
                ((IBuildReceiver) child).setBuild(mBuildInfo);
            }
            if (child instanceof IInvocationContextReceiver) {
                ((IInvocationContextReceiver) child).setInvocationContext(mContext);
            }
            if (child instanceof IMultiDeviceTest) {
                ((IMultiDeviceTest) child).setDeviceInfos(mDeviceInfos);
            }
            try {
                OptionSetter setter = new OptionSetter(child);
                for (String kvPair : mKeyValueOptions) {
                    setter.setOptionValue(HostTest.SET_OPTION_NAME, kvPair);
                }
            } catch (ConfigurationException e) {
                throw new RuntimeException(e);
            }
        }
        // Run the underlying longevity suite.
        suite.run(notifier);
    }

    @Override
    public int testCount() {
        return constructSuite().testCount();
    }
}
