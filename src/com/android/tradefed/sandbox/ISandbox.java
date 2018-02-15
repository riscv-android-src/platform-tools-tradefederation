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
package com.android.tradefed.sandbox;

import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.util.CommandResult;

import java.io.File;

/** Interface defining a sandbox that can be used to run an invocation. */
public interface ISandbox {

    /**
     * Prepare the environment for the sandbox to run properly.
     *
     * @param context the current invocation {@link IInvocationContext}.
     * @param configuration the {@link IConfiguration} for the command to run.
     * @param listener the current invocation {@link ITestInvocationListener} where final results
     *     should be piped.
     * @return an {@link Exception} containing the failure. or Null if successful.
     */
    public Exception prepareEnvironment(
            IInvocationContext context,
            IConfiguration configuration,
            ITestInvocationListener listener);

    /**
     * Run the sandbox with the environment that was set.
     *
     * @param configuration the {@link IConfiguration} for the command to run.
     * @param logger an {@link ITestLogger} where we can log files.
     * @return a {@link CommandResult} with the status of the sandbox run and logs.
     */
    public CommandResult run(IConfiguration configuration, ITestLogger logger) throws Throwable;

    /** Clean up any states, files or environment that may have been changed. */
    public void tearDown();

    /**
     * Returns the sandbox environment TF to be used based on the command line arguments.
     *
     * @param context the {@link IInvocationContext} of the parent.
     * @param nonVersionedConfig the {@link IConfiguration} representing the non versioned objects.
     * @param args the command line arguments.
     * @return a {@link File} directory containing the TF sandbox environment jars.
     */
    public File getTradefedSandboxEnvironment(
            IInvocationContext context, IConfiguration nonVersionedConfig, String[] args)
            throws ConfigurationException;
}
