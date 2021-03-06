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
package com.android.tradefed.lite;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** {@link com.android.tradefed.lite.HostUtils} */
@RunWith(JUnit4.class)
public class HostUtilsTest {
    /**
     * This test checks if our test class is correctly detected to bes annotated with the JUnit
     * annotation.
     */
    @Test
    public void testHasJUnit4Annotation() {
        assertTrue(
                "Has JUnit annotation on crafted test class",
                HostUtils.hasJUnitAnnotation(SampleTests.class));
    }
}
