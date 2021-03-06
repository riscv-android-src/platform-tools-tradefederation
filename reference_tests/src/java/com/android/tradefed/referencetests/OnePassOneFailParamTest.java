/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tradefed.referencetests;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;

import org.junit.runners.Parameterized;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class OnePassOneFailParamTest {
    private String name;
    private Boolean expectedResult;

    public OnePassOneFailParamTest(String name, Boolean expectedResult) {
        this.name = name;
        this.expectedResult = expectedResult;
    }

    @Parameterized.Parameters
    public static Collection paramCollection() {
        return Arrays.asList(
                new Object[][] {
                    {"OnePass", true},
                    {"OneFail", false}
                });
    }

    @Test
    public void testBoolean() {
        assertTrue(expectedResult);
    }
}
