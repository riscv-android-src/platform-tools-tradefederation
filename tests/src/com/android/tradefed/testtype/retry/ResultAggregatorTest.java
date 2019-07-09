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

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.config.ConfigurationDef;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ILogSaver;
import com.android.tradefed.result.ILogSaverListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.retry.ISupportGranularResults;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.HashMap;

/** Unit tests for {@link ResultAggregator}. */
@RunWith(JUnit4.class)
public class ResultAggregatorTest {

    private ResultAggregator mAggregator;
    private ILogSaverListener mAggListener;
    private ITestDetailedReceiver mDetailedListener;
    private IInvocationContext mInvocationContext;
    private IInvocationContext mModuleContext;

    private interface ITestDetailedReceiver
            extends ISupportGranularResults, ITestInvocationListener, ILogSaverListener {}

    @Before
    public void setUp() {
        mAggListener = EasyMock.createMock(ILogSaverListener.class);
        mDetailedListener = EasyMock.createMock(ITestDetailedReceiver.class);
        mInvocationContext = new InvocationContext();
        mInvocationContext.addDeviceBuildInfo(
                ConfigurationDef.DEFAULT_DEVICE_NAME, new BuildInfo());
        mModuleContext = new InvocationContext();
    }

    @Test
    public void testForwarding() {
        TestDescription test1 = new TestDescription("classname", "test1");
        TestDescription test2 = new TestDescription("classname", "test2");
        ILogSaver logger = EasyMock.createMock(ILogSaver.class);

        EasyMock.expect(mDetailedListener.supportGranularResults()).andStubReturn(true);

        // Invocation level
        mAggListener.setLogSaver(logger);
        mAggListener.invocationStarted(mInvocationContext);
        EasyMock.expect(mAggListener.getSummary()).andStubReturn(null);
        mDetailedListener.setLogSaver(logger);
        mDetailedListener.invocationStarted(mInvocationContext);
        EasyMock.expect(mDetailedListener.getSummary()).andStubReturn(null);

        mAggListener.testModuleStarted(mModuleContext);
        mDetailedListener.testModuleStarted(mModuleContext);

        // Detailed receives the breakdown
        mDetailedListener.testRunStarted(
                EasyMock.eq("run1"), EasyMock.eq(2), EasyMock.eq(0), EasyMock.anyLong());
        mDetailedListener.testStarted(EasyMock.eq(test1), EasyMock.anyLong());
        mDetailedListener.testEnded(
                EasyMock.eq(test1),
                EasyMock.anyLong(),
                EasyMock.<HashMap<String, Metric>>anyObject());
        mDetailedListener.testStarted(EasyMock.eq(test2), EasyMock.anyLong());
        mDetailedListener.testFailed(test2, "I failed. retry me.");
        mDetailedListener.testEnded(
                EasyMock.eq(test2),
                EasyMock.anyLong(),
                EasyMock.<HashMap<String, Metric>>anyObject());
        mDetailedListener.testRunEnded(450L, new HashMap<String, Metric>());
        mDetailedListener.testRunStarted(
                EasyMock.eq("run1"), EasyMock.eq(2), EasyMock.eq(1), EasyMock.anyLong());
        mDetailedListener.testStarted(EasyMock.eq(test2), EasyMock.anyLong());
        mDetailedListener.testEnded(
                EasyMock.eq(test2),
                EasyMock.anyLong(),
                EasyMock.<HashMap<String, Metric>>anyObject());
        mDetailedListener.testRunEnded(450L, new HashMap<String, Metric>());

        // Aggregated listeners receives the aggregated results
        mAggListener.testRunStarted(
                EasyMock.eq("run1"), EasyMock.eq(2), EasyMock.eq(0), EasyMock.anyLong());
        mAggListener.testStarted(EasyMock.eq(test1), EasyMock.anyLong());
        mAggListener.testEnded(
                EasyMock.eq(test1),
                EasyMock.anyLong(),
                EasyMock.<HashMap<String, Metric>>anyObject());
        mAggListener.testStarted(EasyMock.eq(test2), EasyMock.anyLong());
        mAggListener.testEnded(
                EasyMock.eq(test2),
                EasyMock.anyLong(),
                EasyMock.<HashMap<String, Metric>>anyObject());
        mAggListener.testRunEnded(450L, new HashMap<String, Metric>());

        mAggListener.testModuleEnded();
        mDetailedListener.testModuleEnded();
        mAggListener.invocationEnded(500L);
        mDetailedListener.invocationEnded(500L);

        EasyMock.replay(mAggListener, mDetailedListener);
        mAggregator =
                new ResultAggregator(
                        Arrays.asList(mAggListener, mDetailedListener),
                        RetryStrategy.RETRY_ANY_FAILURE);
        mAggregator.setLogSaver(logger);
        mAggregator.invocationStarted(mInvocationContext);
        mAggregator.testModuleStarted(mModuleContext);
        // Attempt 1
        mAggregator.testRunStarted("run1", 2, 0);
        mAggregator.testStarted(test1);
        mAggregator.testEnded(test1, new HashMap<String, Metric>());
        mAggregator.testStarted(test2);
        mAggregator.testFailed(test2, "I failed. retry me.");
        mAggregator.testEnded(test2, new HashMap<String, Metric>());
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());
        // Attempt 2
        mAggregator.testRunStarted("run1", 2, 1);
        mAggregator.testStarted(test2);
        mAggregator.testEnded(test2, new HashMap<String, Metric>());
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());

        mAggregator.testModuleEnded();
        mAggregator.invocationEnded(500L);
        EasyMock.verify(mAggListener, mDetailedListener);
    }

    @Test
    public void testForwarding_noModules() {
        TestDescription test1 = new TestDescription("classname", "test1");
        TestDescription test2 = new TestDescription("classname", "test2");
        ILogSaver logger = EasyMock.createMock(ILogSaver.class);

        EasyMock.expect(mDetailedListener.supportGranularResults()).andStubReturn(true);

        // Invocation level
        mAggListener.setLogSaver(logger);
        mAggListener.invocationStarted(mInvocationContext);
        EasyMock.expect(mAggListener.getSummary()).andStubReturn(null);
        mDetailedListener.setLogSaver(logger);
        mDetailedListener.invocationStarted(mInvocationContext);
        EasyMock.expect(mDetailedListener.getSummary()).andStubReturn(null);

        // Detailed receives the breakdown
        mDetailedListener.testRunStarted(
                EasyMock.eq("run1"), EasyMock.eq(2), EasyMock.eq(0), EasyMock.anyLong());
        mDetailedListener.testStarted(EasyMock.eq(test1), EasyMock.anyLong());
        mDetailedListener.testEnded(
                EasyMock.eq(test1),
                EasyMock.anyLong(),
                EasyMock.<HashMap<String, Metric>>anyObject());
        mDetailedListener.testStarted(EasyMock.eq(test2), EasyMock.anyLong());
        mDetailedListener.testFailed(test2, "I failed. retry me.");
        mDetailedListener.testEnded(
                EasyMock.eq(test2),
                EasyMock.anyLong(),
                EasyMock.<HashMap<String, Metric>>anyObject());
        mDetailedListener.testRunEnded(450L, new HashMap<String, Metric>());
        mDetailedListener.testRunStarted(
                EasyMock.eq("run1"), EasyMock.eq(2), EasyMock.eq(1), EasyMock.anyLong());
        mDetailedListener.testStarted(EasyMock.eq(test2), EasyMock.anyLong());
        mDetailedListener.testEnded(
                EasyMock.eq(test2),
                EasyMock.anyLong(),
                EasyMock.<HashMap<String, Metric>>anyObject());
        mDetailedListener.testRunEnded(450L, new HashMap<String, Metric>());

        // Aggregated listeners receives the aggregated results
        mAggListener.testRunStarted(
                EasyMock.eq("run1"), EasyMock.eq(2), EasyMock.eq(0), EasyMock.anyLong());
        mAggListener.testStarted(EasyMock.eq(test1), EasyMock.anyLong());
        mAggListener.testEnded(
                EasyMock.eq(test1),
                EasyMock.anyLong(),
                EasyMock.<HashMap<String, Metric>>anyObject());
        mAggListener.testStarted(EasyMock.eq(test2), EasyMock.anyLong());
        mAggListener.testEnded(
                EasyMock.eq(test2),
                EasyMock.anyLong(),
                EasyMock.<HashMap<String, Metric>>anyObject());
        mAggListener.testRunEnded(900L, new HashMap<String, Metric>());

        mAggListener.invocationEnded(500L);
        mDetailedListener.invocationEnded(500L);

        EasyMock.replay(mAggListener, mDetailedListener);
        mAggregator =
                new ResultAggregator(
                        Arrays.asList(mAggListener, mDetailedListener),
                        RetryStrategy.RETRY_ANY_FAILURE);
        mAggregator.setLogSaver(logger);
        mAggregator.invocationStarted(mInvocationContext);
        // Attempt 1
        mAggregator.testRunStarted("run1", 2, 0);
        mAggregator.testStarted(test1);
        mAggregator.testEnded(test1, new HashMap<String, Metric>());
        mAggregator.testStarted(test2);
        mAggregator.testFailed(test2, "I failed. retry me.");
        mAggregator.testEnded(test2, new HashMap<String, Metric>());
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());
        // Attempt 2
        mAggregator.testRunStarted("run1", 2, 1);
        mAggregator.testStarted(test2);
        mAggregator.testEnded(test2, new HashMap<String, Metric>());
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());

        mAggregator.invocationEnded(500L);
        EasyMock.verify(mAggListener, mDetailedListener);
    }

    /** Test aggregation of results coming from a module first then from a simple test run. */
    @Test
    public void testForwarding_module_noModule() {
        TestDescription test1 = new TestDescription("classname", "test1");
        TestDescription test2 = new TestDescription("classname", "test2");
        ILogSaver logger = EasyMock.createMock(ILogSaver.class);

        EasyMock.expect(mDetailedListener.supportGranularResults()).andStubReturn(true);

        // Invocation level
        mAggListener.setLogSaver(logger);
        mAggListener.invocationStarted(mInvocationContext);
        EasyMock.expect(mAggListener.getSummary()).andStubReturn(null);
        mDetailedListener.setLogSaver(logger);
        mDetailedListener.invocationStarted(mInvocationContext);
        EasyMock.expect(mDetailedListener.getSummary()).andStubReturn(null);

        mAggListener.testModuleStarted(mModuleContext);
        mDetailedListener.testModuleStarted(mModuleContext);

        // Detailed receives the breakdown
        mDetailedListener.testRunStarted(
                EasyMock.eq("run1"), EasyMock.eq(2), EasyMock.eq(0), EasyMock.anyLong());
        mDetailedListener.testStarted(EasyMock.eq(test1), EasyMock.anyLong());
        mDetailedListener.testEnded(
                EasyMock.eq(test1),
                EasyMock.anyLong(),
                EasyMock.<HashMap<String, Metric>>anyObject());
        mDetailedListener.testStarted(EasyMock.eq(test2), EasyMock.anyLong());
        mDetailedListener.testFailed(test2, "I failed. retry me.");
        mDetailedListener.testEnded(
                EasyMock.eq(test2),
                EasyMock.anyLong(),
                EasyMock.<HashMap<String, Metric>>anyObject());
        mDetailedListener.testRunEnded(450L, new HashMap<String, Metric>());
        mDetailedListener.testRunStarted(
                EasyMock.eq("run1"), EasyMock.eq(2), EasyMock.eq(1), EasyMock.anyLong());
        mDetailedListener.testStarted(EasyMock.eq(test2), EasyMock.anyLong());
        mDetailedListener.testEnded(
                EasyMock.eq(test2),
                EasyMock.anyLong(),
                EasyMock.<HashMap<String, Metric>>anyObject());
        mDetailedListener.testRunEnded(450L, new HashMap<String, Metric>());

        // Aggregated listeners receives the aggregated results
        mAggListener.testRunStarted(
                EasyMock.eq("run1"), EasyMock.eq(2), EasyMock.eq(0), EasyMock.anyLong());
        mAggListener.testStarted(EasyMock.eq(test1), EasyMock.anyLong());
        mAggListener.testEnded(
                EasyMock.eq(test1),
                EasyMock.anyLong(),
                EasyMock.<HashMap<String, Metric>>anyObject());
        mAggListener.testStarted(EasyMock.eq(test2), EasyMock.anyLong());
        mAggListener.testEnded(
                EasyMock.eq(test2),
                EasyMock.anyLong(),
                EasyMock.<HashMap<String, Metric>>anyObject());
        mAggListener.testRunEnded(450L, new HashMap<String, Metric>());

        mAggListener.testModuleEnded();
        mDetailedListener.testModuleEnded();

        // Detailed receives the breakdown for non-module
        mDetailedListener.testRunStarted(
                EasyMock.eq("run2"), EasyMock.eq(1), EasyMock.eq(0), EasyMock.anyLong());
        mDetailedListener.testStarted(EasyMock.eq(test1), EasyMock.anyLong());
        mDetailedListener.testFailed(test1, "I failed. retry me.");
        mDetailedListener.testEnded(
                EasyMock.eq(test1),
                EasyMock.anyLong(),
                EasyMock.<HashMap<String, Metric>>anyObject());
        mDetailedListener.testRunEnded(450L, new HashMap<String, Metric>());
        mDetailedListener.testRunStarted(
                EasyMock.eq("run2"), EasyMock.eq(1), EasyMock.eq(1), EasyMock.anyLong());
        mDetailedListener.testStarted(EasyMock.eq(test1), EasyMock.anyLong());
        mDetailedListener.testEnded(
                EasyMock.eq(test1),
                EasyMock.anyLong(),
                EasyMock.<HashMap<String, Metric>>anyObject());
        mDetailedListener.testRunEnded(450L, new HashMap<String, Metric>());

        // Aggregated listeners receives the aggregated results for non-module
        mAggListener.testRunStarted(
                EasyMock.eq("run2"), EasyMock.eq(1), EasyMock.eq(0), EasyMock.anyLong());
        mAggListener.testStarted(EasyMock.eq(test1), EasyMock.anyLong());
        mAggListener.testEnded(
                EasyMock.eq(test1),
                EasyMock.anyLong(),
                EasyMock.<HashMap<String, Metric>>anyObject());
        mAggListener.testRunEnded(900L, new HashMap<String, Metric>());

        mAggListener.invocationEnded(500L);
        mDetailedListener.invocationEnded(500L);

        EasyMock.replay(mAggListener, mDetailedListener);
        mAggregator =
                new ResultAggregator(
                        Arrays.asList(mAggListener, mDetailedListener),
                        RetryStrategy.RETRY_ANY_FAILURE);
        mAggregator.setLogSaver(logger);
        mAggregator.invocationStarted(mInvocationContext);
        mAggregator.testModuleStarted(mModuleContext);
        // Attempt 1
        mAggregator.testRunStarted("run1", 2, 0);
        mAggregator.testStarted(test1);
        mAggregator.testEnded(test1, new HashMap<String, Metric>());
        mAggregator.testStarted(test2);
        mAggregator.testFailed(test2, "I failed. retry me.");
        mAggregator.testEnded(test2, new HashMap<String, Metric>());
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());
        // Attempt 2
        mAggregator.testRunStarted("run1", 2, 1);
        mAggregator.testStarted(test2);
        mAggregator.testEnded(test2, new HashMap<String, Metric>());
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());
        mAggregator.testModuleEnded();

        // New run that is not a module
        mAggregator.testRunStarted("run2", 1, 0);
        mAggregator.testStarted(test1);
        mAggregator.testFailed(test1, "I failed. retry me.");
        mAggregator.testEnded(test1, new HashMap<String, Metric>());
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());

        mAggregator.testRunStarted("run2", 1, 1);
        mAggregator.testStarted(test1);
        mAggregator.testEnded(test1, new HashMap<String, Metric>());
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());

        mAggregator.invocationEnded(500L);
        EasyMock.verify(mAggListener, mDetailedListener);
    }

    /** Test aggregation of results coming from a simple test run first then from a module. */
    @Test
    public void testForwarding_noModule_module() {
        TestDescription test1 = new TestDescription("classname", "test1");
        TestDescription test2 = new TestDescription("classname", "test2");
        ILogSaver logger = EasyMock.createMock(ILogSaver.class);

        EasyMock.expect(mDetailedListener.supportGranularResults()).andStubReturn(true);

        // Invocation level
        mAggListener.setLogSaver(logger);
        mAggListener.invocationStarted(mInvocationContext);
        EasyMock.expect(mAggListener.getSummary()).andStubReturn(null);
        mDetailedListener.setLogSaver(logger);
        mDetailedListener.invocationStarted(mInvocationContext);
        EasyMock.expect(mDetailedListener.getSummary()).andStubReturn(null);

        mAggListener.testModuleStarted(mModuleContext);
        mDetailedListener.testModuleStarted(mModuleContext);

        // Detailed receives the breakdown
        mDetailedListener.testRunStarted(
                EasyMock.eq("run1"), EasyMock.eq(2), EasyMock.eq(0), EasyMock.anyLong());
        mDetailedListener.testStarted(EasyMock.eq(test1), EasyMock.anyLong());
        mDetailedListener.testEnded(
                EasyMock.eq(test1),
                EasyMock.anyLong(),
                EasyMock.<HashMap<String, Metric>>anyObject());
        mDetailedListener.testStarted(EasyMock.eq(test2), EasyMock.anyLong());
        mDetailedListener.testFailed(test2, "I failed. retry me.");
        mDetailedListener.testEnded(
                EasyMock.eq(test2),
                EasyMock.anyLong(),
                EasyMock.<HashMap<String, Metric>>anyObject());
        mDetailedListener.testRunEnded(450L, new HashMap<String, Metric>());
        mDetailedListener.testRunStarted(
                EasyMock.eq("run1"), EasyMock.eq(2), EasyMock.eq(1), EasyMock.anyLong());
        mDetailedListener.testStarted(EasyMock.eq(test2), EasyMock.anyLong());
        mDetailedListener.testEnded(
                EasyMock.eq(test2),
                EasyMock.anyLong(),
                EasyMock.<HashMap<String, Metric>>anyObject());
        mDetailedListener.testRunEnded(450L, new HashMap<String, Metric>());

        // Aggregated listeners receives the aggregated results
        mAggListener.testRunStarted(
                EasyMock.eq("run1"), EasyMock.eq(2), EasyMock.eq(0), EasyMock.anyLong());
        mAggListener.testStarted(EasyMock.eq(test1), EasyMock.anyLong());
        mAggListener.testEnded(
                EasyMock.eq(test1),
                EasyMock.anyLong(),
                EasyMock.<HashMap<String, Metric>>anyObject());
        mAggListener.testStarted(EasyMock.eq(test2), EasyMock.anyLong());
        mAggListener.testEnded(
                EasyMock.eq(test2),
                EasyMock.anyLong(),
                EasyMock.<HashMap<String, Metric>>anyObject());
        mAggListener.testRunEnded(450L, new HashMap<String, Metric>());

        mAggListener.testModuleEnded();
        mDetailedListener.testModuleEnded();

        // Detailed receives the breakdown for non-module
        mDetailedListener.testRunStarted(
                EasyMock.eq("run2"), EasyMock.eq(1), EasyMock.eq(0), EasyMock.anyLong());
        mDetailedListener.testStarted(EasyMock.eq(test1), EasyMock.anyLong());
        mDetailedListener.testFailed(test1, "I failed. retry me.");
        mDetailedListener.testEnded(
                EasyMock.eq(test1),
                EasyMock.anyLong(),
                EasyMock.<HashMap<String, Metric>>anyObject());
        mDetailedListener.testRunEnded(450L, new HashMap<String, Metric>());
        mDetailedListener.testRunStarted(
                EasyMock.eq("run2"), EasyMock.eq(1), EasyMock.eq(1), EasyMock.anyLong());
        mDetailedListener.testStarted(EasyMock.eq(test1), EasyMock.anyLong());
        mDetailedListener.testEnded(
                EasyMock.eq(test1),
                EasyMock.anyLong(),
                EasyMock.<HashMap<String, Metric>>anyObject());
        mDetailedListener.testRunEnded(450L, new HashMap<String, Metric>());

        // Aggregated listeners receives the aggregated results for non-module
        mAggListener.testRunStarted(
                EasyMock.eq("run2"), EasyMock.eq(1), EasyMock.eq(0), EasyMock.anyLong());
        mAggListener.testStarted(EasyMock.eq(test1), EasyMock.anyLong());
        mAggListener.testEnded(
                EasyMock.eq(test1),
                EasyMock.anyLong(),
                EasyMock.<HashMap<String, Metric>>anyObject());
        mAggListener.testRunEnded(900L, new HashMap<String, Metric>());

        mAggListener.invocationEnded(500L);
        mDetailedListener.invocationEnded(500L);

        EasyMock.replay(mAggListener, mDetailedListener);
        mAggregator =
                new ResultAggregator(
                        Arrays.asList(mAggListener, mDetailedListener),
                        RetryStrategy.RETRY_ANY_FAILURE);
        mAggregator.setLogSaver(logger);
        mAggregator.invocationStarted(mInvocationContext);
        // First run that is not a module
        mAggregator.testRunStarted("run2", 1, 0);
        mAggregator.testStarted(test1);
        mAggregator.testFailed(test1, "I failed. retry me.");
        mAggregator.testEnded(test1, new HashMap<String, Metric>());
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());

        mAggregator.testRunStarted("run2", 1, 1);
        mAggregator.testStarted(test1);
        mAggregator.testEnded(test1, new HashMap<String, Metric>());
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());

        // Module start
        mAggregator.testModuleStarted(mModuleContext);
        // Attempt 1
        mAggregator.testRunStarted("run1", 2, 0);
        mAggregator.testStarted(test1);
        mAggregator.testEnded(test1, new HashMap<String, Metric>());
        mAggregator.testStarted(test2);
        mAggregator.testFailed(test2, "I failed. retry me.");
        mAggregator.testEnded(test2, new HashMap<String, Metric>());
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());
        // Attempt 2
        mAggregator.testRunStarted("run1", 2, 1);
        mAggregator.testStarted(test2);
        mAggregator.testEnded(test2, new HashMap<String, Metric>());
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());
        mAggregator.testModuleEnded();

        mAggregator.invocationEnded(500L);
        EasyMock.verify(mAggListener, mDetailedListener);
    }

    /** Test when two modules follow each others. */
    @Test
    public void testForwarding_module_module() {
        TestDescription test1 = new TestDescription("classname", "test1");
        TestDescription test2 = new TestDescription("classname", "test2");
        ILogSaver logger = EasyMock.createMock(ILogSaver.class);

        EasyMock.expect(mDetailedListener.supportGranularResults()).andStubReturn(true);

        // Invocation level
        mAggListener.setLogSaver(logger);
        mAggListener.invocationStarted(mInvocationContext);
        EasyMock.expect(mAggListener.getSummary()).andStubReturn(null);
        mDetailedListener.setLogSaver(logger);
        mDetailedListener.invocationStarted(mInvocationContext);
        EasyMock.expect(mDetailedListener.getSummary()).andStubReturn(null);

        mAggListener.testModuleStarted(mModuleContext);
        mDetailedListener.testModuleStarted(mModuleContext);

        // Detailed receives the breakdown
        mDetailedListener.testRunStarted(
                EasyMock.eq("run1"), EasyMock.eq(2), EasyMock.eq(0), EasyMock.anyLong());
        mDetailedListener.testStarted(EasyMock.eq(test1), EasyMock.anyLong());
        mDetailedListener.testEnded(
                EasyMock.eq(test1),
                EasyMock.anyLong(),
                EasyMock.<HashMap<String, Metric>>anyObject());
        mDetailedListener.testStarted(EasyMock.eq(test2), EasyMock.anyLong());
        mDetailedListener.testFailed(test2, "I failed. retry me.");
        mDetailedListener.testEnded(
                EasyMock.eq(test2),
                EasyMock.anyLong(),
                EasyMock.<HashMap<String, Metric>>anyObject());
        mDetailedListener.testRunEnded(450L, new HashMap<String, Metric>());
        mDetailedListener.testRunStarted(
                EasyMock.eq("run1"), EasyMock.eq(2), EasyMock.eq(1), EasyMock.anyLong());
        mDetailedListener.testStarted(EasyMock.eq(test2), EasyMock.anyLong());
        mDetailedListener.testEnded(
                EasyMock.eq(test2),
                EasyMock.anyLong(),
                EasyMock.<HashMap<String, Metric>>anyObject());
        mDetailedListener.testRunEnded(450L, new HashMap<String, Metric>());

        // Aggregated listeners receives the aggregated results
        mAggListener.testRunStarted(
                EasyMock.eq("run1"), EasyMock.eq(2), EasyMock.eq(0), EasyMock.anyLong());
        mAggListener.testStarted(EasyMock.eq(test1), EasyMock.anyLong());
        mAggListener.testEnded(
                EasyMock.eq(test1),
                EasyMock.anyLong(),
                EasyMock.<HashMap<String, Metric>>anyObject());
        mAggListener.testStarted(EasyMock.eq(test2), EasyMock.anyLong());
        mAggListener.testEnded(
                EasyMock.eq(test2),
                EasyMock.anyLong(),
                EasyMock.<HashMap<String, Metric>>anyObject());
        mAggListener.testRunEnded(450L, new HashMap<String, Metric>());

        mAggListener.testModuleEnded();
        mDetailedListener.testModuleEnded();

        // second module
        mAggListener.testModuleStarted(mModuleContext);
        mDetailedListener.testModuleStarted(mModuleContext);

        mDetailedListener.testRunStarted(
                EasyMock.eq("run2"), EasyMock.eq(1), EasyMock.eq(0), EasyMock.anyLong());
        mDetailedListener.testStarted(EasyMock.eq(test1), EasyMock.anyLong());
        mDetailedListener.testFailed(test1, "I failed. retry me.");
        mDetailedListener.testEnded(
                EasyMock.eq(test1),
                EasyMock.anyLong(),
                EasyMock.<HashMap<String, Metric>>anyObject());
        mDetailedListener.testRunEnded(450L, new HashMap<String, Metric>());
        mDetailedListener.testRunStarted(
                EasyMock.eq("run2"), EasyMock.eq(1), EasyMock.eq(1), EasyMock.anyLong());
        mDetailedListener.testStarted(EasyMock.eq(test1), EasyMock.anyLong());
        mDetailedListener.testEnded(
                EasyMock.eq(test1),
                EasyMock.anyLong(),
                EasyMock.<HashMap<String, Metric>>anyObject());
        mDetailedListener.testRunEnded(450L, new HashMap<String, Metric>());

        // Aggregated listeners receives the aggregated results for non-module
        mAggListener.testRunStarted(
                EasyMock.eq("run2"), EasyMock.eq(1), EasyMock.eq(0), EasyMock.anyLong());
        mAggListener.testStarted(EasyMock.eq(test1), EasyMock.anyLong());
        mAggListener.testEnded(
                EasyMock.eq(test1),
                EasyMock.anyLong(),
                EasyMock.<HashMap<String, Metric>>anyObject());
        mAggListener.testRunEnded(450L, new HashMap<String, Metric>());

        mAggListener.testModuleEnded();
        mDetailedListener.testModuleEnded();

        mAggListener.invocationEnded(500L);
        mDetailedListener.invocationEnded(500L);

        EasyMock.replay(mAggListener, mDetailedListener);
        mAggregator =
                new ResultAggregator(
                        Arrays.asList(mAggListener, mDetailedListener),
                        RetryStrategy.RETRY_ANY_FAILURE);
        mAggregator.setLogSaver(logger);
        mAggregator.invocationStarted(mInvocationContext);

        // Module 1 starts
        mAggregator.testModuleStarted(mModuleContext);
        // Attempt 1
        mAggregator.testRunStarted("run1", 2, 0);
        mAggregator.testStarted(test1);
        mAggregator.testEnded(test1, new HashMap<String, Metric>());
        mAggregator.testStarted(test2);
        mAggregator.testFailed(test2, "I failed. retry me.");
        mAggregator.testEnded(test2, new HashMap<String, Metric>());
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());
        // Attempt 2
        mAggregator.testRunStarted("run1", 2, 1);
        mAggregator.testStarted(test2);
        mAggregator.testEnded(test2, new HashMap<String, Metric>());
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());
        mAggregator.testModuleEnded();

        // Module 2 starts
        mAggregator.testModuleStarted(mModuleContext);
        mAggregator.testRunStarted("run2", 1, 0);
        mAggregator.testStarted(test1);
        mAggregator.testFailed(test1, "I failed. retry me.");
        mAggregator.testEnded(test1, new HashMap<String, Metric>());
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());

        mAggregator.testRunStarted("run2", 1, 1);
        mAggregator.testStarted(test1);
        mAggregator.testEnded(test1, new HashMap<String, Metric>());
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());
        mAggregator.testModuleEnded();

        mAggregator.invocationEnded(500L);
        EasyMock.verify(mAggListener, mDetailedListener);
    }
}
