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

import com.android.annotations.VisibleForTesting;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.ConfigurationFactory;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationFactory;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.IRescheduler;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.TestResult;
import com.android.tradefed.result.TestRunResult;
import com.android.tradefed.result.proto.TestRecordProto.TestRecord;
import com.android.tradefed.testtype.IInvocationContextReceiver;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.suite.BaseTestSuite;
import com.android.tradefed.testtype.suite.SuiteTestFilter;
import com.android.tradefed.util.QuotationAwareTokenizer;
import com.android.tradefed.util.TestRecordInterpreter;

import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

/**
 * A special runner that allows to reschedule a previous run tests that failed or where not
 * executed.
 *
 * <p>TODO: Ensure a configuration should not have several of that runner. Consider having this
 * configuration built-in TF.
 */
public final class RetryRescheduler
        implements IRemoteTest, IConfigurationReceiver, IInvocationContextReceiver {

    /** The types of the tests that can be retried. */
    public enum RetryType {
        FAILED,
        NOT_EXECUTED,
    }

    @Option(
            name = "retry-type",
            description =
                    "used to retry tests of a certain status. Possible values include \"failed\" "
                            + "and \"not_executed\".")
    private RetryType mRetryType = null;

    /**
     * It's possible to add extra exclusion from the rerun. But these tests will not change their
     * state.
     */
    @Option(
        name = BaseTestSuite.EXCLUDE_FILTER_OPTION,
        description = "the exclude module filters to apply.",
        importance = Importance.ALWAYS
    )
    private Set<String> mExcludeFilters = new HashSet<>();

    public static final String PREVIOUS_LOADER_NAME = "previous_loader";

    private IConfiguration mConfiguration;
    private IInvocationContext mContext;
    private IRescheduler mRescheduler;

    private IConfigurationFactory mFactory;

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        // Get the re-loader for previous results
        Object loader = mConfiguration.getConfigurationObject(PREVIOUS_LOADER_NAME);
        if (loader == null) {
            throw new RuntimeException(
                    String.format(
                            "An <object> of type %s was expected in the retry.",
                            PREVIOUS_LOADER_NAME));
        }
        if (!(loader instanceof ITestSuiteResultLoader)) {
            throw new RuntimeException(
                    String.format(
                            "%s should be implementing %s",
                            loader.getClass().getCanonicalName(),
                            ITestSuiteResultLoader.class.getCanonicalName()));
        }
        if (mRescheduler == null) {
            throw new RuntimeException("Failed to find the injected Rescheduler.");
        }
        ITestSuiteResultLoader previousLoader = (ITestSuiteResultLoader) loader;
        // First init the reloader.
        previousLoader.init(mContext.getDevices());
        // Then get the command line of the previous run
        String commandLine = previousLoader.getCommandLine();
        IConfiguration originalConfig;
        try {
            originalConfig =
                    getFactory()
                            .createConfigurationFromArgs(
                                    QuotationAwareTokenizer.tokenizeLine(commandLine));
            // Unset the sharding options for the original command.
            originalConfig.getCommandOptions().setShardCount(null);
            originalConfig.getCommandOptions().setShardIndex(null);
        } catch (ConfigurationException e) {
            throw new RuntimeException(e);
        }
        // Get previous results
        TestRecord previousRecord = previousLoader.loadPreviousRecord();
        CollectingTestListener collectedTests =
                TestRecordInterpreter.interpreteRecord(previousRecord);

        // Appropriately update the configuration
        IRemoteTest test = originalConfig.getTests().get(0);
        if (!(test instanceof BaseTestSuite)) {
            throw new RuntimeException(
                    "RetryScheduler only works for BaseTestSuite implementations");
        }
        BaseTestSuite suite = (BaseTestSuite) test;
        ResultsPlayer replayer = new ResultsPlayer();
        updateRunner(suite, collectedTests, replayer);
        updateConfiguration(originalConfig, replayer);
        // FIXME: This ensure that the retry rescheduler is run against the same device as the
        // rescheduled invocation. This does not work for multi-devices.
        originalConfig
                .getDeviceRequirements()
                .setSerial(mContext.getSerials().toArray(new String[0]));

        // At the end, reschedule
        // TODO(b/110265525): The rescheduling will be done inside the same invocation to avoid
        // tracking issue from higher level components.
        boolean res = mRescheduler.scheduleConfig(originalConfig);
        if (!res) {
            CLog.e("Something went wrong, failed to kick off the retry run.");
        }
    }

    @Inject
    public void setRescheduler(IRescheduler rescheduler) {
        mRescheduler = rescheduler;
    }

    @Override
    public void setConfiguration(IConfiguration configuration) {
        mConfiguration = configuration;
    }

    @Override
    public void setInvocationContext(IInvocationContext invocationContext) {
        mContext = invocationContext;
    }

    private IConfigurationFactory getFactory() {
        if (mFactory != null) {
            return mFactory;
        }
        return ConfigurationFactory.getInstance();
    }

    @VisibleForTesting
    void setConfigurationFactory(IConfigurationFactory factory) {
        mFactory = factory;
    }

    /**
     * Update the configuration to be ready for re-run.
     *
     * @param suite The {@link BaseTestSuite} that will be re-run.
     * @param results The results of the previous run.
     * @param replayer The {@link ResultsPlayer} that will replay the non-retried use cases.
     */
    private void updateRunner(
            BaseTestSuite suite, CollectingTestListener results, ResultsPlayer replayer) {
        List<RetryType> types = new ArrayList<>();
        if (mRetryType == null) {
            types.add(RetryType.FAILED);
            types.add(RetryType.NOT_EXECUTED);
        } else {
            types.add(mRetryType);
        }
        // Prepare exclusion filters
        for (TestRunResult moduleResult : results.getMergedTestRunResults()) {
            // If the module is explicitly excluded from retries, preserve the original results.
            if (!mExcludeFilters.contains(moduleResult.getName())
                    && RetryResultHelper.shouldRunModule(moduleResult, types)) {
                for (Entry<TestDescription, TestResult> result :
                        moduleResult.getTestResults().entrySet()) {
                    if (types.contains(RetryType.NOT_EXECUTED)) {
                        // Clear the run failure since we are attempting to rerun all non-executed
                        moduleResult.testRunFailed(null);
                    }
                    if (!RetryResultHelper.shouldRunTest(result.getValue(), types)) {
                        addExcludeToConfig(suite, moduleResult, result.getKey());
                        replayer.addToReplay(
                                results.getModuleContextForRunResult(moduleResult.getName()),
                                moduleResult,
                                result);
                    }
                }
            } else {
                // Exclude the module completely - it will keep its current status
                addExcludeToConfig(suite, moduleResult, null);
                replayer.addToReplay(
                        results.getModuleContextForRunResult(moduleResult.getName()),
                        moduleResult,
                        null);
            }
        }
    }

    /** Update the configuration to put the replayer before all the actual real tests. */
    private void updateConfiguration(IConfiguration config, ResultsPlayer replayer) {
        List<IRemoteTest> tests = config.getTests();
        List<IRemoteTest> newList = new ArrayList<>();
        // Add the replayer first to replay all the tests cases first.
        newList.add(replayer);
        newList.addAll(tests);
        config.setTests(newList);
    }

    /** Add the filter to the suite. */
    private void addExcludeToConfig(
            BaseTestSuite suite, TestRunResult moduleResult, TestDescription testDescription) {
        String filter = moduleResult.getName();
        if (testDescription != null) {
            filter = String.format("%s %s", filter, testDescription.toString());
        }
        SuiteTestFilter testFilter = SuiteTestFilter.createFrom(filter);
        Set<String> excludeFilter = new LinkedHashSet<>();
        excludeFilter.add(testFilter.toString());
        suite.setExcludeFilter(excludeFilter);
    }
}
