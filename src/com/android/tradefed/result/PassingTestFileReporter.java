/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.ddmlib.testrunner.TestResult.TestStatus;
import com.android.tradefed.config.Option;
import com.android.tradefed.invoker.IInvocationContext;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A {@link ITestInvocationListener} that saves the list of passing test cases to a test file */
public class PassingTestFileReporter extends TestResultListener implements ITestInvocationListener {
    @Option(
            name = "test-file",
            description = "path to test file to write results to",
            mandatory = true)
    private File mTestFilePath;

    private List<String> mResults;
    private Writer mWriter;

    @Override
    public void invocationStarted(IInvocationContext context) {
        mResults = new ArrayList<>();
    }

    @Override
    public void testResult(TestDescription test, TestResult result) {
        if (result.getStatus() == TestStatus.PASSED) {
            mResults.add(String.format("%s#%s\n", test.getClassName(), test.getTestName()));
        }
    }

    @Override
    public void invocationEnded(long elapsedTime) {
        try {
            mTestFilePath.getParentFile().mkdirs();
            mWriter = new BufferedWriter(new FileWriter(mTestFilePath));
            Collections.sort(mResults);
            for (String result : mResults) {
                mWriter.write(result);
            }
            mWriter.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
