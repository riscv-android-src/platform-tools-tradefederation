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
package com.android.tradefed.testtype.retry;

import com.android.tradefed.result.TestRunResult;
import com.android.tradefed.testtype.IRemoteTest;

import java.util.List;

/**
 * Interface driving the retry decision and applying the filter on the class for more targeted
 * retry.
 */
public interface IRetryDecision {

    /**
     * Decide whether or not retry should be attempted. Also make any necessary changes to the
     * {@link IRemoteTest} to be retried (Applying filters, etc.).
     *
     * @param strategy The {@link RetryStrategy} in progress.
     * @param test The {@link IRemoteTest} that just ran.
     * @param previousResults The list of {@link TestRunResult} of the test that just ran.
     * @return True if we should retry, False otherwise.
     */
    public boolean shouldRetry(
            RetryStrategy strategy, IRemoteTest test, List<TestRunResult> previousResults);

    /**
     * {@link #shouldRetry(RetryStrategy, IRemoteTest, List)} will most likely be called before the
     * last retry attempt, so we might be missing the very last attempt results for statistics
     * purpose. This method allows those results to be provided for proper statistics calculations.
     *
     * @param lastResults
     */
    public void addLastAttempt(List<TestRunResult> lastResults);

    /**
     * Returns the {@link RetryStatistics} representing the retry. Or null if the retry strategy is
     * not {@link RetryStrategy#RETRY_ANY_FAILURE}.
     */
    public RetryStatistics getRetryStats();
}
