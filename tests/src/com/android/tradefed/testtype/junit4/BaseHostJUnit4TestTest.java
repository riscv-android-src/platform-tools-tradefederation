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
package com.android.tradefed.testtype.junit4;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.StubDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.HostTest;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Collections;
import java.util.Map;

/** Unit tests for {@link BaseHostJUnit4Test}. */
@RunWith(JUnit4.class)
public class BaseHostJUnit4TestTest {

    /** An implementation of the base class for testing purpose. */
    @RunWith(DeviceJUnit4ClassRunner.class)
    public static class TestableHostJUnit4Test extends BaseHostJUnit4Test {
        @Test
        public void testPass() {
            Assert.assertNotNull(getDevice());
            Assert.assertNotNull(getBuild());
        }

        @Override
        CollectingTestListener createListener() {
            CollectingTestListener listener = new CollectingTestListener();
            listener.testRunStarted("testRun", 1);
            TestDescription tid = new TestDescription("class", "test1");
            listener.testStarted(tid);
            listener.testEnded(tid, Collections.emptyMap());
            listener.testRunEnded(500l, Collections.emptyMap());
            return listener;
        }
    }

    /**
     * An implementation of the base class that simulate a crashed instrumentation from an host test
     * run.
     */
    @RunWith(DeviceJUnit4ClassRunner.class)
    public static class FailureHostJUnit4Test extends BaseHostJUnit4Test {
        @Test
        public void testOne() {
            Assert.assertNotNull(getDevice());
            Assert.assertNotNull(getBuild());
        }

        @Override
        CollectingTestListener createListener() {
            CollectingTestListener listener = new CollectingTestListener();
            listener.testRunStarted("testRun", 1);
            listener.testRunFailed("instrumentation crashed");
            listener.testRunEnded(50L, Collections.emptyMap());
            return listener;
        }
    }

    private static final String CLASSNAME =
            "com.android.tradefed.testtype.junit4.BaseHostJUnit4TestTest$TestableHostJUnit4Test";

    private ITestInvocationListener mMockListener;
    private IBuildInfo mMockBuild;
    private ITestDevice mMockDevice;
    private IInvocationContext mMockContext;
    private HostTest mHostTest;

    @Before
    public void setUp() {
        mMockListener = EasyMock.createMock(ITestInvocationListener.class);
        mMockBuild = EasyMock.createMock(IBuildInfo.class);
        mMockDevice = EasyMock.createMock(ITestDevice.class);
        mMockContext = EasyMock.createMock(IInvocationContext.class);

        mHostTest = new HostTest();
        mHostTest.setBuild(mMockBuild);
        mHostTest.setDevice(mMockDevice);
        mHostTest.setInvocationContext(mMockContext);
    }

    /** Test that we are able to run the test as a JUnit4. */
    @Test
    public void testSimpleRun() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", CLASSNAME);
        mMockListener.testRunStarted(EasyMock.anyObject(), EasyMock.eq(1));
        TestDescription tid = new TestDescription(CLASSNAME, "testPass");
        mMockListener.testStarted(tid);
        mMockListener.testEnded(tid, Collections.emptyMap());
        mMockListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>) EasyMock.anyObject());
        EasyMock.replay(mMockListener, mMockBuild, mMockDevice, mMockContext);
        mHostTest.run(mMockListener);
        EasyMock.verify(mMockListener, mMockBuild, mMockDevice, mMockContext);
    }

    /**
     * Test that {@link BaseHostJUnit4Test#runDeviceTests(String, String)} properly trigger an
     * instrumentation run.
     */
    @Test
    public void testRunDeviceTests() throws Exception {
        TestableHostJUnit4Test test = new TestableHostJUnit4Test();
        test.setDevice(mMockDevice);
        test.setBuild(mMockBuild);
        test.setInvocationContext(mMockContext);
        EasyMock.expect(mMockDevice.getIDevice()).andReturn(new StubDevice("serial"));
        EasyMock.expect(
                        mMockDevice.runInstrumentationTests(
                                (IRemoteAndroidTestRunner) EasyMock.anyObject(),
                                (ITestInvocationListener) EasyMock.anyObject()))
                .andReturn(true);
        EasyMock.replay(mMockBuild, mMockDevice, mMockContext);
        try {
            test.runDeviceTests("com.package", "testClass");
        } catch (AssumptionViolatedException e) {
            // Ensure that the Assume logic in the test does not make a false pass for the unit test
            fail("Should not have thrown an Assume exception.");
        }
        EasyMock.verify(mMockBuild, mMockDevice, mMockContext);
    }

    /**
     * Test that {@link BaseHostJUnit4Test#runDeviceTests(String, String)} properly trigger an
     * instrumentation run as a user.
     */
    @Test
    public void testRunDeviceTests_asUser() throws Exception {
        TestableHostJUnit4Test test = new TestableHostJUnit4Test();
        test.setDevice(mMockDevice);
        test.setBuild(mMockBuild);
        test.setInvocationContext(mMockContext);
        EasyMock.expect(mMockDevice.getIDevice()).andReturn(new StubDevice("serial"));
        EasyMock.expect(
                        mMockDevice.runInstrumentationTestsAsUser(
                                (IRemoteAndroidTestRunner) EasyMock.anyObject(),
                                EasyMock.eq(0),
                                (ITestInvocationListener) EasyMock.anyObject()))
                .andReturn(true);
        EasyMock.replay(mMockBuild, mMockDevice, mMockContext);
        try {
            test.runDeviceTests("package", "class", 0, null);
        } catch (AssumptionViolatedException e) {
            // Ensure that the Assume logic in the test does not make a false pass for the unit test
            fail("Should not have thrown an Assume exception.");
        }
        EasyMock.verify(mMockBuild, mMockDevice, mMockContext);
    }

    /**
     * Test that {@link BaseHostJUnit4Test#runDeviceTests(DeviceTestRunOptions)} properly trigger an
     * instrumentation run.
     */
    @Test
    public void testRunDeviceTestsWithOptions() throws Exception {
        TestableHostJUnit4Test test = new TestableHostJUnit4Test();
        test.setDevice(mMockDevice);
        test.setBuild(mMockBuild);
        test.setInvocationContext(mMockContext);
        EasyMock.expect(mMockDevice.getIDevice()).andReturn(new StubDevice("serial"));
        EasyMock.expect(
                        mMockDevice.runInstrumentationTests(
                                (IRemoteAndroidTestRunner) EasyMock.anyObject(),
                                (ITestInvocationListener) EasyMock.anyObject()))
                .andReturn(true);
        EasyMock.replay(mMockBuild, mMockDevice, mMockContext);
        try {
            test.runDeviceTests(
                    new DeviceTestRunOptions("com.package").setTestClassName("testClass"));
        } catch (AssumptionViolatedException e) {
            // Ensure that the Assume logic in the test does not make a false pass for the unit test
            fail("Should not have thrown an Assume exception.");
        }
        EasyMock.verify(mMockBuild, mMockDevice, mMockContext);
    }

    /**
     * Test that if the instrumentation crash directly we report it as a failure and not an
     * AssumptionFailure (which would improperly categorize the failure).
     */
    @Test
    public void testRunDeviceTests_crashedInstrumentation() throws Exception {
        FailureHostJUnit4Test test = new FailureHostJUnit4Test();
        test.setDevice(mMockDevice);
        test.setBuild(mMockBuild);
        test.setInvocationContext(mMockContext);
        EasyMock.expect(mMockDevice.getIDevice()).andReturn(new StubDevice("serial"));
        EasyMock.expect(
                        mMockDevice.runInstrumentationTests(
                                (IRemoteAndroidTestRunner) EasyMock.anyObject(),
                                (ITestInvocationListener) EasyMock.anyObject()))
                .andReturn(true);
        EasyMock.replay(mMockBuild, mMockDevice, mMockContext);
        try {
            test.runDeviceTests("com.package", "testClass");
        } catch (AssumptionViolatedException e) {
            // Ensure that the Assume logic in the test does not make a false pass for the unit test
            fail("Should not have thrown an Assume exception.");
        } catch (AssertionError expected) {
            assertTrue(expected.getMessage().contains("instrumentation crashed"));
        }
        EasyMock.verify(mMockBuild, mMockDevice, mMockContext);
    }
}
