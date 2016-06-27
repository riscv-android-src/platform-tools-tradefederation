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
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.easymock.EasyMock;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.AnnotatedElement;
import java.util.Map;

/**
 * Unit tests for {@link HostTest}.
 */
@SuppressWarnings("unchecked")
public class HostTestTest extends TestCase {

    private HostTest mHostTest;
    private ITestInvocationListener mListener;

    @MyAnnotation
    public static class SuccessTestCase extends TestCase {
        public SuccessTestCase() {
        }

        public SuccessTestCase(String name) {
            super(name);
        }

        @MyAnnotation
        public void testPass() {
        }

        @MyAnnotation
        @MyAnnotation2
        public void testPass2() {
        }

    }

    @MyAnnotation
    @MyAnnotation2
    public static class AnotherTestCase extends TestCase {
        public AnotherTestCase() {
        }

        public AnotherTestCase(String name) {
            super(name);
        }

        @MyAnnotation
        @MyAnnotation2
        public void testPass3() {
        }

        @MyAnnotation
        public void testPass4() {
        }
    }

    /**
     * Test class, we have to annotate with full org.junit.Test to avoid name collision in import.
     */
    public static class Junit4Testclass {
        public Junit4Testclass() {
        }

        @MyAnnotation
        @MyAnnotation2
        @org.junit.Test
        public void testPass5() {
        }

        @MyAnnotation
        @org.junit.Test
        public void testPass6() {
        }
    }

    @RunWith(Suite.class)
    @SuiteClasses({
        Junit4Testclass.class,
        SuccessTestCase.class,
    })
    public class Junit4Suiteclass {
    }

    /**
     * Simple Annotation class for testing
     */
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MyAnnotation {
    }

    /**
     * Simple Annotation class for testing
     */
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MyAnnotation2 {
    }

    public static class SuccessTestSuite extends TestSuite {
        public SuccessTestSuite() {
            super(SuccessTestCase.class);
        }
    }

    public static class SuccessHierarchySuite extends TestSuite {
        public SuccessHierarchySuite() {
            super();
            addTestSuite(SuccessTestCase.class);
        }
    }

    public static class SuccessDeviceTest extends DeviceTestCase {
        public SuccessDeviceTest() {
            super();
        }

        public void testPass() {
            assertNotNull(getDevice());
        }
    }

    public static class TestRemoteNotCollector extends TestCase implements IDeviceTest,
            IRemoteTest {
        @Override
        public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {}

        @Override
        public void setDevice(ITestDevice device) {}

        @Override
        public ITestDevice getDevice() {
            return null;
        }
    }

    /** Non-public class; should fail to load. */
    private static class PrivateTest extends TestCase {
        @SuppressWarnings("unused")
        public void testPrivate() {
        }
    }

    /** class without default constructor; should fail to load */
    public static class NoConstructorTest extends TestCase {
        public NoConstructorTest(String name) {
            super(name);
        }
        public void testNoConstructor() {
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mHostTest = new HostTest();
        mListener = EasyMock.createMock(ITestInvocationListener.class);
    }

    /**
     * Test success case for {@link HostTest#run(ITestInvocationListener)}, where test to run is a
     * {@link TestCase}.
     */
    public void testRun_testcase() throws Exception {
        mHostTest.setClassName(SuccessTestCase.class.getName());
        TestIdentifier test1 = new TestIdentifier(SuccessTestCase.class.getName(), "testPass");
        TestIdentifier test2 = new TestIdentifier(SuccessTestCase.class.getName(), "testPass2");
        mListener.testRunStarted((String)EasyMock.anyObject(), EasyMock.eq(2));
        mListener.testStarted(EasyMock.eq(test1));
        mListener.testEnded(EasyMock.eq(test1), (Map<String, String>)EasyMock.anyObject());
        mListener.testStarted(EasyMock.eq(test2));
        mListener.testEnded(EasyMock.eq(test2), (Map<String, String>)EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
        EasyMock.verify(mListener);
    }

    /**
     * Test success case for {@link HostTest#run(ITestInvocationListener)}, where test to run is a
     * {@link TestSuite}.
     */
    public void testRun_testSuite() throws Exception {
        mHostTest.setClassName(SuccessTestSuite.class.getName());
        TestIdentifier test1 = new TestIdentifier(SuccessTestCase.class.getName(), "testPass");
        TestIdentifier test2 = new TestIdentifier(SuccessTestCase.class.getName(), "testPass2");
        mListener.testRunStarted((String)EasyMock.anyObject(), EasyMock.eq(2));
        mListener.testStarted(EasyMock.eq(test1));
        mListener.testEnded(EasyMock.eq(test1), (Map<String, String>)EasyMock.anyObject());
        mListener.testStarted(EasyMock.eq(test2));
        mListener.testEnded(EasyMock.eq(test2), (Map<String, String>)EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
        EasyMock.verify(mListener);
    }

    /**
     * Test success case for {@link HostTest#run(ITestInvocationListener)}, where test to run is a
     * hierarchy of {@link TestSuite}s.
     */
    public void testRun_testHierarchySuite() throws Exception {
        mHostTest.setClassName(SuccessHierarchySuite.class.getName());
        TestIdentifier test1 = new TestIdentifier(SuccessTestCase.class.getName(), "testPass");
        TestIdentifier test2 = new TestIdentifier(SuccessTestCase.class.getName(), "testPass2");
        mListener.testRunStarted((String)EasyMock.anyObject(), EasyMock.eq(2));
        mListener.testStarted(EasyMock.eq(test1));
        mListener.testEnded(EasyMock.eq(test1), (Map<String, String>)EasyMock.anyObject());
        mListener.testStarted(EasyMock.eq(test2));
        mListener.testEnded(EasyMock.eq(test2), (Map<String, String>)EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
        EasyMock.verify(mListener);
    }

    /**
     * Test success case for {@link HostTest#run(ITestInvocationListener)}, where test to run is a
     * {@link TestCase} and methodName is set.
     */
    public void testRun_testMethod() throws Exception {
        mHostTest.setClassName(SuccessTestCase.class.getName());
        mHostTest.setMethodName("testPass");
        TestIdentifier test1 = new TestIdentifier(SuccessTestCase.class.getName(), "testPass");
        mListener.testRunStarted((String)EasyMock.anyObject(), EasyMock.eq(1));
        mListener.testStarted(EasyMock.eq(test1));
        mListener.testEnded(EasyMock.eq(test1), (Map<String, String>)EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
        EasyMock.verify(mListener);
    }

    /**
     * Test for {@link HostTest#run(ITestInvocationListener)}, where className is not set.
     */
    public void testRun_missingClass() throws Exception {
        try {
            mHostTest.run(mListener);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Test for {@link HostTest#run(ITestInvocationListener)}, for an invalid class.
     */
    public void testRun_invalidClass() throws Exception {
        try {
            mHostTest.setClassName("foo");
            mHostTest.run(mListener);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Test for {@link HostTest#run(ITestInvocationListener)},
     * for a valid class that is not a {@link Test}.
     */
    public void testRun_notTestClass() throws Exception {
        try {
            mHostTest.setClassName(String.class.getName());
            mHostTest.run(mListener);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Test for {@link HostTest#run(ITestInvocationListener)},
     * for a private class.
     */
    public void testRun_privateClass() throws Exception {
        try {
            mHostTest.setClassName(PrivateTest.class.getName());
            mHostTest.run(mListener);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Test for {@link HostTest#run(ITestInvocationListener)},
     * for a test class with no default constructor.
     */
    public void testRun_noConstructorClass() throws Exception {
        try {
            mHostTest.setClassName(NoConstructorTest.class.getName());
            mHostTest.run(mListener);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Test for {@link HostTest#run(ITestInvocationListener)}, for multiple test classes.
     */
    public void testRun_multipleClass() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", SuccessTestCase.class.getName());
        setter.setOptionValue("class", AnotherTestCase.class.getName());
        TestIdentifier test1 = new TestIdentifier(SuccessTestCase.class.getName(), "testPass");
        TestIdentifier test2 = new TestIdentifier(SuccessTestCase.class.getName(), "testPass2");
        TestIdentifier test3 = new TestIdentifier(AnotherTestCase.class.getName(), "testPass3");
        TestIdentifier test4 = new TestIdentifier(AnotherTestCase.class.getName(), "testPass4");
        mListener.testRunStarted((String)EasyMock.anyObject(), EasyMock.eq(2));
        EasyMock.expectLastCall().times(2);
        mListener.testStarted(EasyMock.eq(test1));
        mListener.testEnded(EasyMock.eq(test1), (Map<String, String>)EasyMock.anyObject());
        mListener.testStarted(EasyMock.eq(test2));
        mListener.testEnded(EasyMock.eq(test2), (Map<String, String>)EasyMock.anyObject());
        mListener.testStarted(EasyMock.eq(test3));
        mListener.testEnded(EasyMock.eq(test3), (Map<String, String>)EasyMock.anyObject());
        mListener.testStarted(EasyMock.eq(test4));
        mListener.testEnded(EasyMock.eq(test4), (Map<String, String>)EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.expectLastCall().times(2);
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
        EasyMock.verify(mListener);
    }

    /**
     * Test for {@link HostTest#run(ITestInvocationListener)},
     * for multiple test classes with a method name.
     */
    public void testRun_multipleClassAndMethodName() throws Exception {
        try {
            OptionSetter setter = new OptionSetter(mHostTest);
            setter.setOptionValue("class", SuccessTestCase.class.getName());
            setter.setOptionValue("class", AnotherTestCase.class.getName());
            mHostTest.setMethodName("testPass3");
            mHostTest.run(mListener);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Test for {@link HostTest#run(ITestInvocationListener)}, for a {@link IDeviceTest}.
     */
    public void testRun_deviceTest() throws Exception {
        final ITestDevice device = EasyMock.createMock(ITestDevice.class);
        mHostTest.setClassName(SuccessDeviceTest.class.getName());
        mHostTest.setDevice(device);

        TestIdentifier test1 = new TestIdentifier(SuccessDeviceTest.class.getName(), "testPass");
        mListener.testRunStarted((String)EasyMock.anyObject(), EasyMock.eq(1));
        mListener.testStarted(EasyMock.eq(test1));
        mListener.testEnded(EasyMock.eq(test1), (Map<String, String>)EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
        EasyMock.verify(mListener);
    }

    /**
     * Test for {@link HostTest#run(ITestInvocationListener)},
     * for a {@link IDeviceTest} where no device has been provided.
     */
    public void testRun_missingDevice() throws Exception {
        mHostTest.setClassName(SuccessDeviceTest.class.getName());
        try {
            mHostTest.run(mListener);
            fail("expected IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Test for {@link HostTest#countTestCases()}
     */
    public void testCountTestCases() throws Exception {
        mHostTest.setClassName(SuccessTestCase.class.getName());
        assertEquals("Incorrect test case count", 2, mHostTest.countTestCases());
    }

    /**
     * Test success case for {@link HostTest#run(ITestInvocationListener)}, where test to run is a
     * {@link TestCase} with annotation filtering.
     */
    public void testRun_testcaseAnnotationFiltering() throws Exception {
        mHostTest.setClassName(SuccessTestCase.class.getName());
        mHostTest.addIncludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation");
        TestIdentifier test1 = new TestIdentifier(SuccessTestCase.class.getName(), "testPass");
        TestIdentifier test2 = new TestIdentifier(SuccessTestCase.class.getName(), "testPass2");
        mListener.testRunStarted((String)EasyMock.anyObject(), EasyMock.eq(2));
        mListener.testStarted(EasyMock.eq(test1));
        mListener.testEnded(EasyMock.eq(test1), (Map<String, String>)EasyMock.anyObject());
        mListener.testStarted(EasyMock.eq(test2));
        mListener.testEnded(EasyMock.eq(test2), (Map<String, String>)EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
        EasyMock.verify(mListener);
    }

    /**
     * Test success case for {@link HostTest#run(ITestInvocationListener)}, where test to run is a
     * {@link TestCase} with notAnnotationFiltering
     */
    public void testRun_testcaseNotAnnotationFiltering() throws Exception {
        mHostTest.setClassName(SuccessTestCase.class.getName());
        mHostTest.addExcludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation2");
        TestIdentifier test1 = new TestIdentifier(SuccessTestCase.class.getName(), "testPass");
        // Only test1 will run, test2 should be filtered out.
        mListener.testRunStarted((String)EasyMock.anyObject(), EasyMock.eq(1));
        mListener.testStarted(EasyMock.eq(test1));
        mListener.testEnded(EasyMock.eq(test1), (Map<String, String>)EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
        EasyMock.verify(mListener);
    }

    /**
     * Test success case for {@link HostTest#run(ITestInvocationListener)}, where test to run is a
     * {@link TestCase} with both annotation filtering.
     */
    public void testRun_testcaseBothAnnotationFiltering() throws Exception {
        mHostTest.setClassName(AnotherTestCase.class.getName());
        mHostTest.addIncludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation");
        mHostTest.addExcludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation2");
        TestIdentifier test4 = new TestIdentifier(AnotherTestCase.class.getName(), "testPass4");
        // Only a test with MyAnnotation and Without MyAnnotation2 will run. Here testPass4
        mListener.testRunStarted((String)EasyMock.anyObject(), EasyMock.eq(1));
        mListener.testStarted(EasyMock.eq(test4));
        mListener.testEnded(EasyMock.eq(test4), (Map<String, String>)EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
        EasyMock.verify(mListener);
    }

    /**
     * Test success case for {@link HostTest#run(ITestInvocationListener)}, where test to run is a
     * {@link TestCase} with multiple include annotation, test must contains them all.
     */
    public void testRun_testcaseMultiInclude() throws Exception {
        mHostTest.setClassName(AnotherTestCase.class.getName());
        mHostTest.addIncludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation");
        mHostTest.addIncludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation2");
        TestIdentifier test3 = new TestIdentifier(AnotherTestCase.class.getName(), "testPass3");
        // Only a test with MyAnnotation and with MyAnnotation2 will run. Here testPass3
        mListener.testRunStarted((String)EasyMock.anyObject(), EasyMock.eq(1));
        mListener.testStarted(EasyMock.eq(test3));
        mListener.testEnded(EasyMock.eq(test3), (Map<String, String>)EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
        EasyMock.verify(mListener);
    }

    /**
     * Test success case for {@link HostTest#shouldTestRun(AnnotatedElement)}, where a class is
     * properly annotated to run.
     */
    public void testRun_shouldTestRun_Success() throws Exception {
        mHostTest.addIncludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation");
        assertTrue(mHostTest.shouldTestRun(SuccessTestCase.class));
    }

    /**
     * Test success case for {@link HostTest#shouldTestRun(AnnotatedElement)}, where a class is
     * properly annotated to run with multiple annotation expected.
     */
    public void testRun_shouldTestRunMulti_Success() throws Exception {
        mHostTest.addIncludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation");
        mHostTest.addIncludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation2");
        assertTrue(mHostTest.shouldTestRun(AnotherTestCase.class));
    }

    /**
     * Test case for {@link HostTest#shouldTestRun(AnnotatedElement)}, where a class is
     * properly annotated to be filtered.
     */
    public void testRun_shouldNotRun() throws Exception {
        mHostTest.addExcludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation");
        assertFalse(mHostTest.shouldTestRun(SuccessTestCase.class));
    }

    /**
     * Test case for {@link HostTest#shouldTestRun(AnnotatedElement)}, where a class is
     * properly annotated to be filtered because one of its two annotations is part of the exclude.
     */
    public void testRun_shouldNotRunMulti() throws Exception {
        mHostTest.addExcludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation");
        assertFalse(mHostTest.shouldTestRun(AnotherTestCase.class));
        mHostTest = new HostTest();
        // If only the other annotation is excluded.
        mHostTest.addExcludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation2");
        assertFalse(mHostTest.shouldTestRun(AnotherTestCase.class));
    }

    /**
     * Test success case for {@link HostTest#shouldTestRun(AnnotatedElement)}, where a class is
     * annotated with a different annotation from the exclude filter.
     */
    public void testRun_shouldRun_exclude() throws Exception {
        mHostTest.addExcludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation2");
        assertTrue(mHostTest.shouldTestRun(SuccessTestCase.class));
    }

    /**
     * Test success case for {@link HostTest#run(ITestInvocationListener)}, where test to run is a
     * {@link TestCase} with annotation filtering.
     */
    public void testRun_testcaseCollectMode() throws Exception {
        mHostTest.setClassName(SuccessTestCase.class.getName());
        mHostTest.setCollectTestsOnly(true);
        mListener.testRunStarted((String)EasyMock.anyObject(), EasyMock.eq(2));
        mListener.testStarted((TestIdentifier) EasyMock.anyObject());
        mListener.testEnded((TestIdentifier) EasyMock.anyObject(),
                (Map<String, String>)EasyMock.anyObject());
        mListener.testStarted((TestIdentifier) EasyMock.anyObject());
        mListener.testEnded((TestIdentifier) EasyMock.anyObject(),
                (Map<String, String>)EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
        EasyMock.verify(mListener);
    }

    /**
     * Test success case for {@link HostTest#run(ITestInvocationListener)}, where the
     * {@link IRemoteTest} does not implements {@link ITestCollector}
     */
    public void testRun_testcaseCollectMode_IRemotedevice() throws Exception {
        final ITestDevice device = EasyMock.createMock(ITestDevice.class);
        mHostTest.setClassName(TestRemoteNotCollector.class.getName());
        mHostTest.setDevice(device);
        mHostTest.setCollectTestsOnly(true);
        EasyMock.replay(mListener);
        try {
            mHostTest.run(mListener);
        } catch (IllegalArgumentException expected) {
            EasyMock.verify(mListener);
            return;
        }
        fail("HostTest run() should have thrown an exception.");
    }

    /**
     * Test for {@link HostTest#run(ITestInvocationListener)}, for test with Junit4 style.
     */
    public void testRun_junit4style() throws Exception {
        mHostTest.setClassName(Junit4Testclass.class.getName());
        TestIdentifier test1 = new TestIdentifier(Junit4Testclass.class.getName(), "testPass5");
        TestIdentifier test2 = new TestIdentifier(Junit4Testclass.class.getName(), "testPass6");
        mListener.testRunStarted((String)EasyMock.anyObject(), EasyMock.eq(2));
        mListener.testStarted(EasyMock.eq(test1));
        mListener.testEnded(EasyMock.eq(test1), (Map<String, String>)EasyMock.anyObject());
        mListener.testStarted(EasyMock.eq(test2));
        mListener.testEnded(EasyMock.eq(test2), (Map<String, String>)EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
        EasyMock.verify(mListener);
    }

    /**
     * Test for {@link HostTest#run(ITestInvocationListener)}, for a mix of test junit3 and 4
     */
    public void testRun_junit_version_mix() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", SuccessTestCase.class.getName());
        setter.setOptionValue("class", Junit4Testclass.class.getName());
        runMixJunitTest(mHostTest, 2, 2);
    }

    /**
     * Test for {@link HostTest#run(ITestInvocationListener)}, for a mix of test junit3 and 4 in
     * collect only mode
     */
    public void testRun_junit_version_mix_collect() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", SuccessTestCase.class.getName());
        setter.setOptionValue("class", Junit4Testclass.class.getName());
        setter.setOptionValue("collect-tests-only", "true");
        runMixJunitTest(mHostTest, 2, 2);
    }

    /**
     * Test for {@link HostTest#run(ITestInvocationListener)}, for a mix of test junit3 and 4 in
     * a Junit 4 suite class.
     */
    public void testRun_junit_suite_mix() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", Junit4Suiteclass.class.getName());
        runMixJunitTest(mHostTest, 4, 1);
    }

    /**
     * Test for {@link HostTest#run(ITestInvocationListener)}, for a mix of test junit3 and 4 in
     * a Junit 4 suite class, in collect only mode.
     */
    public void testRun_junit_suite_mix_collect() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", Junit4Suiteclass.class.getName());
        setter.setOptionValue("collect-tests-only", "true");
        runMixJunitTest(mHostTest, 4, 1);
    }

    /**
     * Helper for test option variation and avoid repeating the same setup
     */
    private void runMixJunitTest(HostTest hostTest, int expectedTest, int expectedRun)
            throws Exception {
        TestIdentifier test1 = new TestIdentifier(SuccessTestCase.class.getName(), "testPass");
        TestIdentifier test2 = new TestIdentifier(SuccessTestCase.class.getName(), "testPass2");
        TestIdentifier test3 = new TestIdentifier(Junit4Testclass.class.getName(), "testPass5");
        TestIdentifier test4 = new TestIdentifier(Junit4Testclass.class.getName(), "testPass6");
        mListener.testRunStarted((String)EasyMock.anyObject(), EasyMock.eq(expectedTest));
        EasyMock.expectLastCall().times(expectedRun);
        mListener.testStarted(EasyMock.eq(test1));
        mListener.testEnded(EasyMock.eq(test1), (Map<String, String>)EasyMock.anyObject());
        mListener.testStarted(EasyMock.eq(test2));
        mListener.testEnded(EasyMock.eq(test2), (Map<String, String>)EasyMock.anyObject());
        mListener.testStarted(EasyMock.eq(test3));
        mListener.testEnded(EasyMock.eq(test3), (Map<String, String>)EasyMock.anyObject());
        mListener.testStarted(EasyMock.eq(test4));
        mListener.testEnded(EasyMock.eq(test4), (Map<String, String>)EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.expectLastCall().times(expectedRun);
        EasyMock.replay(mListener);
        hostTest.run(mListener);
        EasyMock.verify(mListener);
    }

    /**
     * Test success case for {@link HostTest#run(ITestInvocationListener)} with a filtering and
     * junit 4 handling.
     */
    public void testRun_testcase_Junit4TestNotAnnotationFiltering() throws Exception {
        mHostTest.setClassName(Junit4Testclass.class.getName());
        mHostTest.addExcludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation2");
        TestIdentifier test1 = new TestIdentifier(Junit4Testclass.class.getName(), "testPass6");
        // Only test1 will run, test2 should be filtered out.
        mListener.testRunStarted((String)EasyMock.anyObject(), EasyMock.eq(1));
        mListener.testStarted(EasyMock.eq(test1));
        mListener.testEnded(EasyMock.eq(test1), (Map<String, String>)EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
        EasyMock.verify(mListener);
    }

    /**
     * Test success case for {@link HostTest#run(ITestInvocationListener)}, where filtering is
     * applied and results in 0 tests to run.
     */
    public void testRun_testcase_Junit4Test_filtering_no_more_tests() throws Exception {
        mHostTest.setClassName(Junit4Testclass.class.getName());
        mHostTest.addExcludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation");
        mListener.testRunStarted((String)EasyMock.anyObject(), EasyMock.eq(0));
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
        EasyMock.verify(mListener);
    }

    /**
     * Test for {@link HostTest#run(ITestInvocationListener)}, for a mix of test junit3 and 4 in
     * a Junit 4 suite class, and filtering is applied.
     */
    public void testRun_junit_suite_mix_filtering() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", Junit4Suiteclass.class.getName());
        runMixJunitTestWithFilter(mHostTest);
    }

    /**
     * Test for {@link HostTest#run(ITestInvocationListener)}, for a mix of test junit3 and 4 in
     * a Junit 4 suite class, and filtering is applied, in collect mode
     */
    public void testRun_junit_suite_mix_filtering_collect() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", Junit4Suiteclass.class.getName());
        setter.setOptionValue("collect-tests-only", "true");
        runMixJunitTestWithFilter(mHostTest);
    }

    /**
     * Helper for test option variation and avoid repeating the same setup
     */
    private void runMixJunitTestWithFilter(HostTest hostTest)
            throws Exception {
        hostTest.addExcludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation2");
        TestIdentifier test1 = new TestIdentifier(SuccessTestCase.class.getName(), "testPass");
        TestIdentifier test4 = new TestIdentifier(Junit4Testclass.class.getName(), "testPass6");
        mListener.testRunStarted((String)EasyMock.anyObject(), EasyMock.eq(2));
        EasyMock.expectLastCall().times(1);
        mListener.testStarted(EasyMock.eq(test1));
        mListener.testEnded(EasyMock.eq(test1), (Map<String, String>)EasyMock.anyObject());
        mListener.testStarted(EasyMock.eq(test4));
        mListener.testEnded(EasyMock.eq(test4), (Map<String, String>)EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.expectLastCall().times(1);
        EasyMock.replay(mListener);
        hostTest.run(mListener);
        EasyMock.verify(mListener);
    }
}
