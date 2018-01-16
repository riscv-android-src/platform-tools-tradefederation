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
package com.android.tradefed.result;

import com.android.tradefed.testtype.DeviceTestCase;

import junit.framework.AssertionFailedError;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Collections;
import java.util.Map;

/** Unit tests for {@link JUnitToInvocationResultForwarder}. */
@RunWith(JUnit4.class)
public class JUnitToInvocationResultForwarderTest {

    private ITestInvocationListener mListener;
    private JUnitToInvocationResultForwarder mForwarder;

    @Before
    public void setUp() throws Exception {
        mListener = EasyMock.createMock(ITestInvocationListener.class);
        mForwarder = new JUnitToInvocationResultForwarder(mListener);
    }

    /**
     * Test method for {@link JUnitToInvocationResultForwarder#addFailure(junit.framework.Test,
     * AssertionFailedError)}.
     */
    @Test
    public void testAddFailure() {
        final AssertionFailedError a = new AssertionFailedError();
        mListener.testFailed(
                EasyMock.eq(new TestDescription(DeviceTestCase.class.getName(), "testAddFailure")),
                (String) EasyMock.anyObject());
        EasyMock.replay(mListener);
        DeviceTestCase test = new DeviceTestCase();
        test.setName("testAddFailure");
        mForwarder.addFailure(test, a);
        EasyMock.verify(mListener);
    }

    /** Test method for {@link JUnitToInvocationResultForwarder#endTest(junit.framework.Test)}. */
    @Test
    public void testEndTest() {
        Map<String, String> emptyMap = Collections.emptyMap();
        mListener.testEnded(
                EasyMock.eq(new TestDescription(DeviceTestCase.class.getName(), "testEndTest")),
                EasyMock.eq(emptyMap));
        DeviceTestCase test = new DeviceTestCase();
        test.setName("testEndTest");
        EasyMock.replay(mListener);
        mForwarder.endTest(test);
        EasyMock.verify(mListener);
    }

    /** Test method for {@link JUnitToInvocationResultForwarder#startTest(junit.framework.Test)}. */
    @Test
    public void testStartTest() {
        mListener.testStarted(
                EasyMock.eq(new TestDescription(DeviceTestCase.class.getName(), "testStartTest")));
        DeviceTestCase test = new DeviceTestCase();
        test.setName("testStartTest");
        EasyMock.replay(mListener);
        mForwarder.startTest(test);
        EasyMock.verify(mListener);
    }
}
