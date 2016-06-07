/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tradefed.util.CommandResult;

import junit.framework.TestCase;

public class PythonUnitTestRunnerTest extends TestCase {

    private PythonUnitTestRunner mRunner;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mRunner = new PythonUnitTestRunner();
    }

    public void testCheckPythonVersion_276given270min() {
        CommandResult c = new CommandResult();
        c.setStderr("Python 2.7.6");
        mRunner.checkPythonVersion(c);
    }

    public void testCheckPythonVersion_276given331min() {
        CommandResult c = new CommandResult();
        c.setStderr("Python 2.7.6");
        mRunner.setMinPythonVersion("3.3.1");
        try {
            mRunner.checkPythonVersion(c);
            fail("Detected 2.7.6 >= 3.3.1");
        } catch (AssertionError e) {
            return;
        }
    }

    public void testCheckPythonVersion_300given276min() {
        CommandResult c = new CommandResult();
        c.setStderr("Python 3.0.0");
        mRunner.setMinPythonVersion("2.7.6");
        mRunner.checkPythonVersion(c);
    }
}