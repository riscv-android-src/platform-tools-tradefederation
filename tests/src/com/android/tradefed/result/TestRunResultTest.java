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
package com.android.tradefed.result;

import static org.junit.Assert.*;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ddmlib.testrunner.TestResult.TestStatus;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Collections;

/** Unit tests for {@link TestRunResult} */
@RunWith(JUnit4.class)
public class TestRunResultTest {

    /** Check basic storing of results when events are coming in. */
    @Test
    public void testGetNumTestsInState() {
        TestIdentifier test = new TestIdentifier("FooTest", "testBar");
        TestRunResult result = new TestRunResult();
        assertEquals(0, result.getNumTestsInState(TestStatus.PASSED));
        result.testStarted(test);
        assertEquals(0, result.getNumTestsInState(TestStatus.PASSED));
        assertEquals(1, result.getNumTestsInState(TestStatus.INCOMPLETE));
        result.testEnded(test, Collections.emptyMap());
        assertEquals(1, result.getNumTestsInState(TestStatus.PASSED));
        assertEquals(0, result.getNumTestsInState(TestStatus.INCOMPLETE));
        // Ensure our test was recorded.
        assertNotNull(result.getTestResults().get(test));
    }

    /** Check basic storing of results when events are coming in and there is a test failure. */
    @Test
    public void testGetNumTestsInState_failed() {
        TestIdentifier test = new TestIdentifier("FooTest", "testBar");
        TestRunResult result = new TestRunResult();
        assertEquals(0, result.getNumTestsInState(TestStatus.PASSED));
        result.testStarted(test);
        assertEquals(0, result.getNumTestsInState(TestStatus.PASSED));
        assertEquals(1, result.getNumTestsInState(TestStatus.INCOMPLETE));
        result.testFailed(test, "I failed!");
        // Test status immediately switch to failure.
        assertEquals(0, result.getNumTestsInState(TestStatus.PASSED));
        assertEquals(1, result.getNumTestsInState(TestStatus.FAILURE));
        assertEquals(0, result.getNumTestsInState(TestStatus.INCOMPLETE));
        result.testEnded(test, Collections.emptyMap());
        assertEquals(0, result.getNumTestsInState(TestStatus.PASSED));
        assertEquals(1, result.getNumTestsInState(TestStatus.FAILURE));
        assertEquals(0, result.getNumTestsInState(TestStatus.INCOMPLETE));
        // Ensure our test was recorded.
        assertNotNull(result.getTestResults().get(test));
    }

    /** Test that we are able to specify directly the start and end time of a test. */
    @Test
    public void testSpecifyElapsedTime() {
        TestIdentifier test = new TestIdentifier("FooTest", "testBar");
        TestRunResult result = new TestRunResult();
        result.testStarted(test, 5L);
        assertEquals(5L, result.getTestResults().get(test).getStartTime());
        result.testEnded(test, 25L, Collections.emptyMap());
        assertEquals(25L, result.getTestResults().get(test).getEndTime());
    }
}
