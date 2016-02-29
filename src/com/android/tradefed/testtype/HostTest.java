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

import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A test runner for JUnit host based tests. If the test to be run implements {@link IDeviceTest}
 * this runner will pass a reference to the device.
 */
@OptionClass(alias = "host")
public class HostTest implements IDeviceTest, ITestFilterReceiver, IRemoteTest {

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

    private ITestDevice mDevice;
    private Set<String> mIncludes = new HashSet<>();
    private Set<String> mExcludes = new HashSet<>();

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
        mIncludes.add(filter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAllIncludeFilters(List<String> filters) {
        mIncludes.addAll(filters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addExcludeFilter(String filter) {
        mExcludes.add(filter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAllExcludeFilters(List<String> filters) {
        mExcludes.addAll(filters);
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

    void addIncludeAnnotation(String annotation) {
        mIncludeAnnotation.add(annotation);
    }

    void addExcludeAnnotation(String notAnnotation) {
        mExcludeAnnotation.add(notAnnotation);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
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
                List<String> includes = new ArrayList<>(mIncludes);
                if (mMethodName != null) {
                    includes.add(String.format("%s#%s", classObj.getName(), mMethodName));
                }
                List<String> excludes = new ArrayList<>(mExcludes);
                if (test instanceof ITestFilterReceiver) {
                    ((ITestFilterReceiver) test).addAllIncludeFilters(includes);
                    ((ITestFilterReceiver) test).addAllExcludeFilters(excludes);
                } else if (!includes.isEmpty() || !excludes.isEmpty()) {
                    throw new IllegalArgumentException(String.format(
                            "%s does not implement ITestFilterReceiver", classObj.getName()));
                }
                if (shouldTestRun(test.getClass())) {
                    test.run(listener);
                }
            } else if (Test.class.isAssignableFrom(classObj)) {
                JUnitRunUtil.runTest(listener, collectTests(collectClasses(classObj)),
                        classObj.getName());
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
                        || !shouldRun(packageName, className, method)) {
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

    /**
     * Check if an elements that has annotation pass the filter.
     * @param annotatedElement
     * @return false if the test should not run.
     */
    protected boolean shouldTestRun(AnnotatedElement annotatedElement) {
        if (!mExcludeAnnotation.isEmpty()) {
            for(Annotation a : annotatedElement.getAnnotations()) {
                if (mExcludeAnnotation.contains(a.annotationType().getName())) {
                    // If any of the method annotation match an ExcludeAnnotation, don't run it
                    CLog.i("Skipping %s, ExcludeAnnotation exclude it", annotatedElement);
                    return false;
                }
            }
        }
        if (!mIncludeAnnotation.isEmpty()) {
            Set<String> neededAnnotation = new HashSet<String>();
            neededAnnotation.addAll(mIncludeAnnotation);
            for(Annotation a : annotatedElement.getAnnotations()) {
                if (neededAnnotation.contains(a.annotationType().getName())) {
                    neededAnnotation.remove(a.annotationType().getName());
                }
            }
            if (neededAnnotation.size() != 0) {
                // The test needs to have all the include annotation to pass.
                CLog.i("Skipping %s, IncludeAnnotation filtered it", annotatedElement);
                return false;
            }
        }
        return true;
    }

    private boolean shouldRun(String packageName, String className, Method method) {
        String methodName = String.format("%s#%s", className, method.getName());
        if (mExcludes.contains(packageName)) {
            // Skip package because it was excluded
            CLog.i("Skip package because it was excluded");
            return false;
        }
        if (mExcludes.contains(className)) {
            // Skip class because it was excluded
            CLog.i("Skip class because it was excluded");
            return false;
        }
        if (mExcludes.contains(methodName)) {
            // Skip method because it was excluded
            CLog.i("Skip method because it was excluded");
            return false;
        }
        if (!shouldTestRun(method)) {
            return false;
        }
        return mIncludes.isEmpty()
                || mIncludes.contains(methodName)
                || mIncludes.contains(className)
                || mIncludes.contains(packageName);
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
}
