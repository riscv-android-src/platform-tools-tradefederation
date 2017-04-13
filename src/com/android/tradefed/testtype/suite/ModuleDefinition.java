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
import com.android.ddmlib.testrunner.TestResult;
import com.android.ddmlib.testrunner.TestRunResult;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceUnresponsiveException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.ResultForwarder;
import com.android.tradefed.suite.checker.ISystemStatusCheckerReceiver;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetCleaner;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.ITestCollector;
import com.android.tradefed.util.StreamUtil;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Container for the test run configuration. This class is an helper to prepare and run the tests.
 */
public class ModuleDefinition implements Comparable<ModuleDefinition>, ITestCollector {

    protected static final String MODULE_INCOMPLETE_MSG = "Module did not run all its tests.";

    private final String mId;
    private List<IRemoteTest> mTests = null;
    private List<ITargetPreparer> mPreparers = new ArrayList<>();
    private List<ITargetCleaner> mCleaners = new ArrayList<>();
    private IBuildInfo mBuild;
    private ITestDevice mDevice;

    private List<TestRunResult> mTestsResults = new ArrayList<>();
    private int mExpectedTests = 0;
    private boolean mIsFailedModule = false;

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
        run(listener, null);
    }

    /**
     * Run all the {@link IRemoteTest} contained in the module and use all the preparers before and
     * after to setup and clean the device.
     *
     * @param listener the {@link ITestInvocationListener} where to report results.
     * @param failureListener a particular listener to collect logs on testFail. Can be null.
     * @throws DeviceNotAvailableException in case of device going offline.
     */
    public void run(ITestInvocationListener listener, TestFailureListener failureListener)
            throws DeviceNotAvailableException {
        CLog.d("Running module %s", getId());
        Exception preparationException = null;
        // Setup
        for (ITargetPreparer preparer : mPreparers) {
            preparationException = runPreparerSetup(preparer);
            if (preparationException != null) {
                mIsFailedModule = true;
                CLog.e("Some preparation step failed. failing the module %s", getId());
                break;
            }
        }
        // Run the tests
        try {
            if (preparationException != null) {
                // For reporting purpose we create a failure placeholder with the error stack
                // similar to InitializationError of JUnit.
                TestIdentifier testid = new TestIdentifier(getId(), "PreparationError");
                listener.testRunStarted(getId(), 1);
                listener.testStarted(testid);
                StringWriter sw = new StringWriter();
                preparationException.printStackTrace(new PrintWriter(sw));
                listener.testFailed(testid, sw.toString());
                listener.testEnded(testid, Collections.emptyMap());
                listener.testRunFailed(sw.toString());
                listener.testRunEnded(0, Collections.emptyMap());
                return;
            }
            for (IRemoteTest test : mTests) {
                if (test instanceof IBuildReceiver) {
                    ((IBuildReceiver) test).setBuild(mBuild);
                }
                if (test instanceof IDeviceTest) {
                    ((IDeviceTest) test).setDevice(mDevice);
                }
                if (test instanceof ISystemStatusCheckerReceiver) {
                    // We do not pass down Status checker because they are already running at the
                    // top level suite.
                    ((ISystemStatusCheckerReceiver) test).setSystemStatusChecker(new ArrayList<>());
                }

                // Run the test, only in case of DeviceNotAvailable we exit the module
                // execution in order to execute as much as possible.
                ModuleListener moduleListener = new ModuleListener(listener);
                List<ITestInvocationListener> currentTestListener = new ArrayList<>();
                if (failureListener != null) {
                    currentTestListener.add(failureListener);
                }
                currentTestListener.add(moduleListener);

                try {
                    test.run(new ResultForwarder(currentTestListener));
                } catch (RuntimeException re) {
                    CLog.e("Module '%s' - test '%s' threw exception:", getId(), test.getClass());
                    CLog.e(re);
                    CLog.e("Proceeding to the next test.");
                } catch (DeviceUnresponsiveException due) {
                    // being able to catch a DeviceUnresponsiveException here implies that
                    // recovery was successful, and test execution should proceed to next
                    // module.
                    ByteArrayOutputStream stack = new ByteArrayOutputStream();
                    due.printStackTrace(new PrintWriter(stack, true));
                    StreamUtil.close(stack);
                    CLog.w(
                            "Ignored DeviceUnresponsiveException because recovery was "
                                    + "successful, proceeding with next module. Stack trace: %s",
                            stack.toString());
                    CLog.w("Proceeding to the next test.");
                } finally {
                    mTestsResults.addAll(moduleListener.getRunResults());
                    mExpectedTests += moduleListener.getNumTotalTests();
                }
            }
        } finally {
            // finalize results
            if (preparationException == null) {
                reportFinalResults(listener, mExpectedTests, mTestsResults);
            }
            // Tear down
            for (ITargetCleaner cleaner : mCleaners) {
                CLog.d("Cleaner: %s", cleaner.getClass().getSimpleName());
                cleaner.tearDown(mDevice, mBuild, null);
            }
        }
    }

    /** Finalize results to report them all and count if there are missing tests. */
    private void reportFinalResults(
            ITestInvocationListener listener,
            int totalExpectedTests,
            List<TestRunResult> listResults) {
        long elapsedTime = 0l;
        Map<String, String> metrics = new HashMap<>();
        listener.testRunStarted(getId(), totalExpectedTests);

        int numResults = 0;
        for (TestRunResult runResult : listResults) {
            numResults += runResult.getTestResults().size();
            forwardTestResults(runResult.getTestResults(), listener);
            if (runResult.isRunFailure()) {
                listener.testRunFailed(runResult.getRunFailureMessage());
                mIsFailedModule = true;
            }
            elapsedTime += runResult.getElapsedTime();
            metrics.putAll(runResult.getRunMetrics());
        }

        if (totalExpectedTests != numResults) {
            listener.testRunFailed(MODULE_INCOMPLETE_MSG);
            CLog.e(
                    "Module %s only ran %d out of %d expected tests.",
                    getId(), numResults, totalExpectedTests);
            mIsFailedModule = true;
        }
        listener.testRunEnded(elapsedTime, metrics);
    }

    private void forwardTestResults(
            Map<TestIdentifier, TestResult> testResults, ITestInvocationListener listener) {
        for (Map.Entry<TestIdentifier, TestResult> testEntry : testResults.entrySet()) {
            listener.testStarted(testEntry.getKey(), testEntry.getValue().getStartTime());
            switch (testEntry.getValue().getStatus()) {
                case FAILURE:
                    listener.testFailed(testEntry.getKey(), testEntry.getValue().getStackTrace());
                    break;
                case ASSUMPTION_FAILURE:
                    listener.testAssumptionFailure(
                            testEntry.getKey(), testEntry.getValue().getStackTrace());
                    break;
                case IGNORED:
                    listener.testIgnored(testEntry.getKey());
                    break;
                case INCOMPLETE:
                    listener.testFailed(
                            testEntry.getKey(), "Test did not complete due to exception.");
                    break;
                default:
                    break;
            }
            listener.testEnded(
                    testEntry.getKey(),
                    testEntry.getValue().getEndTime(),
                    testEntry.getValue().getMetrics());
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

    @Override
    public void setCollectTestsOnly(boolean collectTestsOnly) {
        for (IRemoteTest test : mTests) {
            ((ITestCollector) test).setCollectTestsOnly(collectTestsOnly);
        }
    }

    /** Returns a list of tests that ran in this module. */
    List<TestRunResult> getTestsResults() {
        return mTestsResults;
    }

    /** Returns the number of tests that was expected to be run */
    int getNumExpectedTests() {
        return mExpectedTests;
    }

    /** Returns True if a testRunFailure has been called on the module * */
    public boolean hasModuleFailed() {
        return mIsFailedModule;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return getId();
    }
}
