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
package com.android.tradefed.testtype;

import com.android.ddmlib.Log;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.util.TestFilterHelper;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestResult;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

/**
 * Helper JUnit test case that provides the {@link IRemoteTest} and {@link IDeviceTest} services.
 * <p/>
 * This is useful if you want to implement tests that follow the JUnit pattern of defining tests,
 * and still have full support for other tradefed features such as {@link Option}s
 */
public class DeviceTestCase extends TestCase implements IDeviceTest, IRemoteTest, ITestCollector,
        ITestFilterReceiver {

    private static final String LOG_TAG = "DeviceTestCase";
    private ITestDevice mDevice;
    private TestFilterHelper mFilterHelper;

    /** The include filters of the test name to run */
    @Option(name = "include-filter",
            description="The include filters of the test name to run.")
    protected List<String> mIncludeFilters = new ArrayList<>();

    /** The exclude filters of the test name to run */
    @Option(name = "exclude-filter",
            description="The exclude filters of the test name to run.")
    protected List<String> mExcludeFilters = new ArrayList<>();

    /** The include annotations of the test to run */
    @Option(name="include-annotation",
            description="The set of annotations a test must have to be run.")
    protected Set<String> mIncludeAnnotation = new HashSet<String>();

    /** The exclude annotations of the test to run */
    @Option(name="exclude-annotation",
            description="The name of class for the notAnnotation filter to be used.")
    protected Set<String> mExcludeAnnotation = new HashSet<String>();

    @Option(name = "method", description = "run a specific test method.")
    private String mMethodName = null;

    @Option(name = "collect-tests-only",
            description = "Only invoke the instrumentation to collect list of applicable test "
                    + "cases. All test run callbacks will be triggered, but test execution will "
                    + "not be actually carried out.")
    private boolean mCollectTestsOnly = false;

    private Vector<String> mMethodNames = null;

    public DeviceTestCase() {
        super();
        mFilterHelper = new TestFilterHelper(mIncludeFilters, mExcludeFilters,
                mIncludeAnnotation, mExcludeAnnotation);
    }

    public DeviceTestCase(String name) {
        super(name);
        mFilterHelper = new TestFilterHelper(mIncludeFilters, mExcludeFilters,
                mIncludeAnnotation, mExcludeAnnotation);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        if (getName() == null && mMethodName != null) {
            setName(mMethodName);
        }
        if (mFilterHelper.shouldTestRun(this.getClass())) {
            if (mCollectTestsOnly) {
                // Collect only mode, fake the junit test execution.
                Map<String, String> empty = Collections.emptyMap();
                String runName = this.getClass().getName();
                if (getName() == null) {
                    Collection<String> testMethodNames = getTestMethodNames();
                    listener.testRunStarted(runName, testMethodNames.size());
                    for (String methodName : testMethodNames) {
                        TestIdentifier testId =
                                new TestIdentifier(runName, methodName);
                        listener.testStarted(testId);
                        listener.testEnded(testId, empty);
                    }
                    listener.testRunEnded(0, empty);
                } else {
                    listener.testRunStarted(runName, 1);
                    TestIdentifier testId =
                            new TestIdentifier(runName, getName());
                    listener.testStarted(testId);
                    listener.testEnded(testId, empty);
                    listener.testRunEnded(0, empty);
                }
            } else {
                JUnitRunUtil.runTest(listener, this);
            }
        }
    }

    @Override
    public int countTestCases() {
        // the superclass implementation always returns 1 - add logic to handle case where all tests
        // should be run
        if (getName() != null || mMethodName != null) {
            return 1;
        } else {
            return getTestMethodNames().size();
        }
    }

    /**
     * Override parent method to run all test methods if test method to run is null.
     * <p/>
     * The JUnit framework only supports running all the tests in a TestCase by wrapping it in a
     * TestSuite. Unfortunately with this mechanism callers can't control the lifecycle of their
     * own test cases, which makes it impossible to do things like have the tradefed configuration
     * framework inject options into a Test Case.
     */
    @Override
    public void run(TestResult result) {
        // check if test method to run aka name is null
        if (getName() == null) {
            Collection<String> testMethodNames = getTestMethodNames();
            for (String methodName : testMethodNames) {
                setName(methodName);
                CLog.d("Running %s#%s()", this.getClass().getName(), methodName);
                super.run(result);
            }
        } else {
            CLog.d("Running %s#%s()", this.getClass().getName(), getName());
            super.run(result);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addIncludeFilter(String filter) {
        mIncludeFilters.add(filter);
        mFilterHelper.addIncludeFilter(filter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAllIncludeFilters(List<String> filters) {
        mIncludeFilters.addAll(filters);
        mFilterHelper.addAllIncludeFilters(filters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addExcludeFilter(String filter) {
        mExcludeFilters.add(filter);
        mFilterHelper.addExcludeFilter(filter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAllExcludeFilters(List<String> filters) {
        mExcludeFilters.addAll(filters);
        mFilterHelper.addAllExcludeFilters(filters);
    }

    void addIncludeAnnotation(String annotation) {
        mIncludeAnnotation.add(annotation);
        mFilterHelper.addIncludeAnnotation(annotation);
    }

    void addExcludeAnnotation(String notAnnotation) {
        mExcludeAnnotation.add(notAnnotation);
        mFilterHelper.addExcludeAnnotation(notAnnotation);
    }

    /**
     * Get list of test methods to run
     *
     * @return a {@link Collection} of string that contains all the method names.
     */
    private Collection<String> getTestMethodNames() {
        if (mMethodNames == null) {
            mMethodNames = new Vector<String>();
            // Unfortunately {@link TestSuite} doesn't expose the functionality to find all test*
            // methods,
            // so needed to copy and paste code from TestSuite
            Class<?> theClass = this.getClass();
            Class<?> superClass = theClass;
            while (Test.class.isAssignableFrom(superClass)) {
                Method[] methods = superClass.getDeclaredMethods();
                for (Method method : methods) {
                    if (mFilterHelper.shouldRun(
                            theClass.getPackage().getName(), theClass.getName(), method)) {
                        addTestMethod(method, mMethodNames);
                    }
                }
                superClass = superClass.getSuperclass();
            }
            if (mMethodNames.size() == 0) {
                Log.w(LOG_TAG, String.format("No tests found in %s", theClass.getName()));
            }
        }
        return mMethodNames;
    }

    private void addTestMethod(Method m, Vector<String> names) {
        String name = m.getName();
        if (names.contains(name)) {
            return;
        }
        if (!isPublicTestMethod(m)) {
            if (isTestMethod(m)) {
                Log.w(LOG_TAG, String.format("Test method isn't public: %s", m.getName()));
            }
            return;
        }
        names.addElement(name);
    }

    private boolean isPublicTestMethod(Method m) {
        return isTestMethod(m) && Modifier.isPublic(m.getModifiers());
    }

    private boolean isTestMethod(Method m) {
        String name = m.getName();
        Class<?>[] parameters = m.getParameterTypes();
        Class<?> returnType = m.getReturnType();
        return parameters.length == 0 && name.startsWith("test") && returnType.equals(Void.TYPE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCollectTestsOnly(boolean shouldCollectTest) {
        mCollectTestsOnly = shouldCollectTest;
    }
}
