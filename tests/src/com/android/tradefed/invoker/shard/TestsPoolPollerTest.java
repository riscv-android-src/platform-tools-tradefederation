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
package com.android.tradefed.invoker.shard;

import static org.junit.Assert.*;

import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.StubTest;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** Unit tests for {@link TestsPoolPoller}. */
public class TestsPoolPollerTest {

    /**
     * Tests that {@link TestsPoolPoller#poll()} returns a {@link IRemoteTest} from the pool or null
     * when the pool is empty.
     */
    @Test
    public void testMultiPolling() {
        int numTests = 5;
        List<IRemoteTest> testsList = new ArrayList<>();
        for (int i = 0; i < numTests; i++) {
            testsList.add(new StubTest());
        }
        TestsPoolPoller poller1 = new TestsPoolPoller(testsList);
        TestsPoolPoller poller2 = new TestsPoolPoller(testsList);
        // initial size
        assertEquals(numTests, testsList.size());
        assertNotNull(poller1.poll());
        assertEquals(numTests - 1, testsList.size());
        assertNotNull(poller2.poll());
        assertEquals(numTests - 2, testsList.size());
        assertNotNull(poller1.poll());
        assertNotNull(poller1.poll());
        assertNotNull(poller2.poll());
        assertTrue(testsList.isEmpty());
        // once empty poller returns null
        assertNull(poller1.poll());
        assertNull(poller2.poll());
    }
}
