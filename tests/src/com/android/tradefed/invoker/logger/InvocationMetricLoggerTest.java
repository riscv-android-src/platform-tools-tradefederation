/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tradefed.invoker.logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Map;
import java.util.UUID;

/** Unit tests for {@link InvocationMetricLogger}. */
@RunWith(JUnit4.class)
public class InvocationMetricLoggerTest {

    @Test
    public void testLogMetrics() throws Exception {
        String uniqueKey = UUID.randomUUID().toString();
        Map<String, String> result = logMetric(uniqueKey, "TEST");
        assertEquals("TEST", result.get(uniqueKey));
        // Ensure that it wasn't added in current ThreadGroup
        assertNull(InvocationMetricLogger.getInvocationMetrics().get(uniqueKey));
    }

    private Map<String, String> logMetric(String key, String value) throws Exception {
        String uuid = UUID.randomUUID().toString();
        ThreadGroup testGroup = new ThreadGroup("unit-test-group-" + uuid);
        TestRunnable runnable = new TestRunnable(key, value);
        Thread testThread = new Thread(testGroup, runnable);
        testThread.setName("InvocationMetricLoggerTest-test-thread");
        testThread.setDaemon(true);
        testThread.start();
        testThread.join(10000);
        return runnable.getResultMap();
    }

    private class TestRunnable implements Runnable {

        private String mKey;
        private String mValue;
        private Map<String, String> mResultMap;

        public TestRunnable(String key, String value) {
            mKey = key;
            mValue = value;
        }

        @Override
        public void run() {
            InvocationMetricLogger.addInvocationMetrics(mKey, mValue);
            mResultMap = InvocationMetricLogger.getInvocationMetrics();
        }

        public Map<String, String> getResultMap() {
            return mResultMap;
        }
    }
}
