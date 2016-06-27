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

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.util.TestFilterHelper;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A test runner for JUnit host based tests. If the test to be run implements {@link IDeviceTest}
 * this runner will pass a reference to the device.
 */
@OptionClass(alias = "host")
public class HostTest implements IDeviceTest, ITestFilterReceiver, ITestAnnotationFilterReceiver,
        IRemoteTest, ITestCollector {

    @Option(name="class", description="The JUnit test classes to run, in the format "
            + "<package>.<class>. eg. \"com.android.foo.Bar\". This field can be repeated.",
            importance = Importance.IF_UNSET)
    private Set<String> mClasses = new HashSet<>();

    @Option(name="method", description="The name of the method in the JUnit TestCase to run. "
            + "eg. \"testFooBar\"",
            importance = Importance.IF_UNSET)
    private String mMethodName;

    @Option(name="set-option", description = "Options to be passed down to the class "
            + "under test, key and value should be separated by colon \":\"; for example, if class "
            + "under test supports \"--iteration 1\" from a command line, it should be passed in as"
            + " \"--set-option iteration:1\"; escaping of \":\" is currently not supported")
    private List<String> mKeyValueOptions = new ArrayList<>();

    @Option(name="include-annotation",
            description="The set of annotations a test must have to be run.")
    private Set<String> mIncludeAnnotation = new HashSet<String>();

    @Option(name="exclude-annotation",
            description="The name of class for the notAnnotation filter to be used.")
    private Set<String> mExcludeAnnotation = new HashSet<String>();

    @Option(name = "collect-tests-only",
            description = "Only invoke the instrumentation to collect list of applicable test "
                    + "cases. All test run callbacks will be triggered, but test execution will "
                    + "not be actually carried out.")
    private boolean mCollectTestsOnly = false;

    private ITestDevice mDevice;
    private TestFilterHelper mFilterHelper;

    public HostTest() {
        mFilterHelper = new TestFilterHelper(new ArrayList<String>(), new ArrayList<String>(),
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
    public void addIncludeFilter(String filter) {
        mFilterHelper.addIncludeFilter(filter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAllIncludeFilters(Set<String> filters) {
        mFilterHelper.addAllIncludeFilters(filters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addExcludeFilter(String filter) {
        mFilterHelper.addExcludeFilter(filter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAllExcludeFilters(Set<String> filters) {
        mFilterHelper.addAllExcludeFilters(filters);
    }

    /**
     * Return the number of test cases across all classes part of the tests
     */
    public int countTestCases() {
        int count = 0;
        for (Class<?> classObj : getClasses()) {
            Object testObj = loadObject(classObj);
            if (testObj instanceof Test) {
                if (testObj instanceof TestCase
                        && !(testObj instanceof DeviceTestCase)) {
                    // wrap the test in a suite, because the JUnit TestCase.run implementation can
                    // only run a single method
                    count += new TestSuite(classObj).countTestCases();
                } else {
                    count += ((Test) testObj).countTestCases();
                }
            } else {
                count++;
            }
        }
        return count;
    }

    void setClassName(String className) {
        mClasses.clear();
        mClasses.add(className);
    }

    void setMethodName(String methodName) {
        mMethodName = methodName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addIncludeAnnotation(String annotation) {
        mIncludeAnnotation.add(annotation);
        mFilterHelper.addIncludeAnnotation(annotation);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAllIncludeAnnotation(Set<String> annotations) {
        mIncludeAnnotation.addAll(annotations);
        mFilterHelper.addAllIncludeAnnotation(annotations);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addExcludeAnnotation(String notAnnotation) {
        mExcludeAnnotation.add(notAnnotation);
        mFilterHelper.addExcludeAnnotation(notAnnotation);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAllExcludeAnnotation(Set<String> notAnnotations) {
        mExcludeAnnotation.addAll(notAnnotations);
        mFilterHelper.addAllExcludeAnnotation(notAnnotations);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        // Ensure filters are set in the helper
        mFilterHelper.addAllIncludeAnnotation(mIncludeAnnotation);
        mFilterHelper.addAllExcludeAnnotation(mExcludeAnnotation);

        List<Class<?>> classes = getClasses();
        if (classes.isEmpty()) {
            throw new IllegalArgumentException("Missing Test class name");
        }
        if (mMethodName != null && classes.size() > 1) {
            throw new IllegalArgumentException("Method name given with multiple test classes");
        }
        for (Class<?> classObj : classes) {
            if (IRemoteTest.class.isAssignableFrom(classObj)) {
                IRemoteTest test = (IRemoteTest) loadObject(classObj);
                Set<String> includes = mFilterHelper.getIncludeFilters();
                if (mMethodName != null) {
                    includes.add(String.format("%s#%s", classObj.getName(), mMethodName));
                }
                Set<String> excludes = mFilterHelper.getExcludeFilters();
                if (test instanceof ITestFilterReceiver) {
                    ((ITestFilterReceiver) test).addAllIncludeFilters(includes);
                    ((ITestFilterReceiver) test).addAllExcludeFilters(excludes);
                } else if (!includes.isEmpty() || !excludes.isEmpty()) {
                    throw new IllegalArgumentException(String.format(
                            "%s does not implement ITestFilterReceiver", classObj.getName()));
                }
                if (test instanceof ITestAnnotationFilterReceiver) {
                    ((ITestAnnotationFilterReceiver) test).addAllIncludeAnnotation(
                            mIncludeAnnotation);
                    ((ITestAnnotationFilterReceiver) test).addAllExcludeAnnotation(
                            mExcludeAnnotation);
                }
                if (mFilterHelper.shouldTestRun(test.getClass())) {
                    if (mCollectTestsOnly) {
                        // Collect only mode is propagated to the test.
                        if (test instanceof ITestCollector) {
                            ((ITestCollector)test).setCollectTestsOnly(true);
                        } else {
                            throw new IllegalArgumentException(String.format(
                                    "%s does not implement ITestCollector", test.getClass()));
                        }
                    }
                    test.run(listener);
                }
            } else if (Test.class.isAssignableFrom(classObj)) {
                if (mCollectTestsOnly) {
                    // Collect only mode, fake the junit test execution.
                    TestSuite junitTest = collectTests(collectClasses(classObj));
                    listener.testRunStarted(classObj.getName(), junitTest.countTestCases());
                    Map<String, String> empty = Collections.emptyMap();
                    for (int i = 0; i < junitTest.countTestCases(); i++) {
                        Test t = junitTest.testAt(i);
                        // Test does not have a getName method.
                        // using the toString format instead: <testName>(className)
                        String testName = t.toString().split("\\(")[0];
                        TestIdentifier testId =
                                new TestIdentifier(t.getClass().getCanonicalName(), testName);
                        listener.testStarted(testId);
                        listener.testEnded(testId, empty);
                    }
                    Map<String, String> emptyMap = Collections.emptyMap();
                    listener.testRunEnded(0, emptyMap);
                } else {
                    JUnitRunUtil.runTest(listener, collectTests(collectClasses(classObj)),
                            classObj.getName());
                }
            } else {
                // TODO: Add junit4 support here
                throw new IllegalArgumentException(
                        String.format("%s is not a test", classObj.getName()));
            }
        }
    }

    private Set<Class<?>> collectClasses(Class<?> classObj) {
        Set<Class<?>> classes = new HashSet<>();
        if (TestSuite.class.isAssignableFrom(classObj)) {
            TestSuite testObj = (TestSuite) loadObject(classObj);
            classes.addAll(getClassesFromSuite(testObj));
        } else {
            classes.add(classObj);
        }
        return classes;
    }

    private Set<Class<?>> getClassesFromSuite(TestSuite suite) {
        Set<Class<?>> classes = new HashSet<>();
        Enumeration<Test> tests = suite.tests();
        while (tests.hasMoreElements()) {
            Test test = tests.nextElement();
            if (test instanceof TestSuite) {
                classes.addAll(getClassesFromSuite((TestSuite) test));
            } else {
                classes.addAll(collectClasses(test.getClass()));
            }
        }
        return classes;
    }

    private TestSuite collectTests(Set<Class<?>> classes) {
        TestSuite suite = new TestSuite();
        for (Class<?> classObj : classes) {
            String packageName = classObj.getPackage().getName();
            String className = classObj.getName();
            Method[] methods = null;
            if (mMethodName == null) {
                methods = classObj.getMethods();
            } else {
                try {
                    methods = new Method[] {
                            classObj.getMethod(mMethodName, (Class[]) null)
                    };
                } catch (NoSuchMethodException e) {
                    throw new IllegalArgumentException(
                            String.format("Cannot find %s#%s", className, mMethodName), e);
                }
            }
            for (Method method : methods) {
                if (!Modifier.isPublic(method.getModifiers())
                        || !method.getReturnType().equals(Void.TYPE)
                        || method.getParameterTypes().length > 0
                        || !method.getName().startsWith("test")
                        || !mFilterHelper.shouldRun(packageName, className, method)) {
                    continue;
                }
                Test testObj = (Test) loadObject(classObj);
                if (testObj instanceof TestCase) {
                    ((TestCase)testObj).setName(method.getName());
                }
                suite.addTest(testObj);
            }
        }
        return suite;
    }

    protected List<Class<?>> getClasses() throws IllegalArgumentException  {
        List<Class<?>> classes = new ArrayList<>();
        for (String className : mClasses) {
            try {
                classes.add(Class.forName(className));
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException(String.format("Could not load Test class %s",
                        className), e);
            }
        }
        return classes;
    }

    protected Object loadObject(Class<?> classObj) throws IllegalArgumentException {
        final String className = classObj.getName();
        try {
            Object testObj = classObj.newInstance();
            // set options
            if (!mKeyValueOptions.isEmpty()) {
                try {
                    OptionSetter setter = new OptionSetter(testObj);
                    for (String item : mKeyValueOptions) {
                        String[] fields = item.split(":");
                        if (fields.length == 2) {
                            setter.setOptionValue(fields[0], fields[1]);
                        } else if (fields.length == 3) {
                            setter.setOptionValue(fields[0], fields[1], fields[2]);
                        } else {
                            throw new RuntimeException(
                                String.format("invalid option spec \"%s\"", item));
                        }
                    }
                } catch (ConfigurationException ce) {
                    throw new RuntimeException("error passing options down to test class", ce);
                }
            }
            if (testObj instanceof IDeviceTest) {
                if (mDevice == null) {
                    throw new IllegalArgumentException("Missing device");
                }
                ((IDeviceTest)testObj).setDevice(mDevice);
            }
            return testObj;
        } catch (InstantiationException e) {
            throw new IllegalArgumentException(String.format("Could not load Test class %s",
                    className), e);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException(String.format("Could not load Test class %s",
                    className), e);
        }
    }

    /**
     * Check if an elements that has annotation pass the filter. Exposed for unit testing.
     * @param annotatedElement
     * @return false if the test should not run.
     */
    protected boolean shouldTestRun(AnnotatedElement annotatedElement) {
        return mFilterHelper.shouldTestRun(annotatedElement);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCollectTestsOnly(boolean shouldCollectTest) {
        mCollectTestsOnly = shouldCollectTest;
    }

}
