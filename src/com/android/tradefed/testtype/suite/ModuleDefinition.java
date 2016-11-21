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

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetCleaner;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Container for the test run configuration. This class is an helper to prepare and run the tests.
 */
public class ModuleDefinition implements Comparable<ModuleDefinition> {

    private final String mId;
    private List<IRemoteTest> mTests = null;
    private List<ITargetPreparer> mPreparers = new ArrayList<>();
    private List<ITargetCleaner> mCleaners = new ArrayList<>();
    private IBuildInfo mBuild;
    private ITestDevice mDevice;

    /**
     * Constructor
     *
     * @param name unique name of the test configuration.
     * @param tests list of {@link IRemoteTest} that needs to run.
     * @param preparers list of {@link ITargetPreparer} to be used to setup the device.
     */
    public ModuleDefinition(String name, List<IRemoteTest> tests, List<ITargetPreparer> preparers) {
        mId = name;
        mTests = tests;
        for (ITargetPreparer preparer : preparers) {
            mPreparers.add(preparer);
            if (preparer instanceof ITargetCleaner) {
                mCleaners.add((ITargetCleaner) preparer);
            }
        }
        // Reverse cleaner order, so that last target_preparer to setup is first to clean up.
        Collections.reverse(mCleaners);
    }

    /**
     * Return the unique module name.
     */
    public String getId() {
        return mId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int compareTo(ModuleDefinition moduleDef) {
        return getId().compareTo(moduleDef.getId());
    }

    /**
     * Inject the {@link IBuildInfo} to be used during the tests.
     */
    public void setBuild(IBuildInfo build) {
        mBuild = build;
    }

    /**
     * Inject the {@link ITestDevice} to be used during the tests.
     */
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /**
     * Run all the {@link IRemoteTest} contained in the module and use all the preparers before and
     * after to setup and clean the device.
     *
     * @param listener the {@link ITestInvocationListener} where to report results.
     * @throws DeviceNotAvailableException in case of device going offline.
     */
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        ModuleListener moduleListener = new ModuleListener(getId(), listener);
        CLog.d("Running module %s", getId());
        Exception preparationException = null;
        // Setup
        for (ITargetPreparer preparer : mPreparers) {
            preparationException = runPreparerSetup(preparer);
            if (preparationException != null) {
                CLog.e("Some preparation step failed. failing the module %s", getId());
                break;
            }
        }
        // Run the tests
        try {
            for (IRemoteTest test : mTests) {
                CLog.d("Test: %s", test.getClass().getSimpleName());
                if (test instanceof IBuildReceiver) {
                    ((IBuildReceiver) test).setBuild(mBuild);
                }
                if (test instanceof IDeviceTest) {
                    ((IDeviceTest) test).setDevice(mDevice);
                }
                if (preparationException == null) {
                    test.run(moduleListener);
                } else {
                    // For reporting purpose we create a failure placeholder with the error stack
                    // similar to InitializationError of JUnit.
                    TestIdentifier testid = new TestIdentifier(
                            test.getClass().getCanonicalName(), "PreparationError");
                    moduleListener.testRunStarted(getId(), 1);
                    moduleListener.testStarted(testid);
                    StringWriter sw = new StringWriter();
                    preparationException.printStackTrace(new PrintWriter(sw));
                    moduleListener.testFailed(testid, sw.toString());
                    moduleListener.testEnded(testid, Collections.emptyMap());
                    moduleListener.testRunFailed(sw.toString());
                    moduleListener.testRunEnded(0, Collections.emptyMap());
                }
            }
        } finally {
            // Tear down
            for (ITargetCleaner cleaner : mCleaners) {
                CLog.d("Cleaner: %s", cleaner.getClass().getSimpleName());
                cleaner.tearDown(mDevice, mBuild, null);
            }
        }
    }

    /**
     * Run all the prepare steps.
     */
    private Exception runPreparerSetup(ITargetPreparer preparer)
            throws DeviceNotAvailableException {
        CLog.d("Preparer: %s", preparer.getClass().getSimpleName());
        try {
            preparer.setUp(mDevice, mBuild);
            return null;
        } catch (BuildError | TargetSetupError e) {
            CLog.e("Unexpected Exception from preparer: %s", preparer.getClass().getName());
            CLog.e(e);
            return e;
        }
    }
}
