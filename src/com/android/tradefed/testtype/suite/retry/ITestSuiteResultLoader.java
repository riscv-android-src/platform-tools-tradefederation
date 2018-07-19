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
package com.android.tradefed.testtype.suite.retry;

import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.result.suite.SuiteResultHolder;

/** Interface describing an helper to load previous results in a way that can be re-run. */
public interface ITestSuiteResultLoader {

    /** Initialization of the loader. */
    public void init(IInvocationContext context);

    /** Retrieve the original command line from the previous run. */
    public String getCommandLine();

    /** Load the previous results in a {@link SuiteResultHolder} format. */
    public SuiteResultHolder loadPreviousResults();
}
