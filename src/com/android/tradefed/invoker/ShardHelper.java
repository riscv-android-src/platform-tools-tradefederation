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
package com.android.tradefed.invoker;

import com.android.tradefed.build.ExistingBuildProvider;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.IShardableListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.IShardableTest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Helper class that handles creating the shards and scheduling them for an invocation. */
public class ShardHelper {

    /**
     * Attempt to shard the configuration into sub-configurations, to be re-scheduled to run on
     * multiple resources in parallel.
     *
     * <p>A successful shard action renders the current config empty, and invocation should not
     * proceed.
     *
     * @see IShardableTest
     * @see IRescheduler
     * @param config the current {@link IConfiguration}.
     * @param context the {@link IInvocationContext} holding the tests information.
     * @param rescheduler the {@link IRescheduler}
     * @return true if test was sharded. Otherwise return <code>false</code>
     */
    public static boolean legacyShardConfig(
            IConfiguration config, IInvocationContext context, IRescheduler rescheduler) {
        List<IRemoteTest> shardableTests = new ArrayList<IRemoteTest>();
        boolean isSharded = false;
        for (IRemoteTest test : config.getTests()) {
            isSharded |= shardTest(shardableTests, test);
        }
        if (!isSharded) {
            return false;
        }
        // shard this invocation!
        // create the TestInvocationListener that will collect results from all the shards,
        // and forward them to the original set of listeners (minus any ISharddableListeners)
        // once all shards complete
        ShardMasterResultForwarder resultCollector =
                new ShardMasterResultForwarder(
                        config.getLogSaver(),
                        buildMasterShardListeners(config),
                        shardableTests.size());

        resultCollector.invocationStarted(context);
        for (IRemoteTest testShard : shardableTests) {
            CLog.i("Rescheduling sharded config...");
            IConfiguration shardConfig = config.clone();
            shardConfig.setTest(testShard);

            cloneBuildInfos(config, shardConfig, context);

            shardConfig.setTestInvocationListeners(
                    buildShardListeners(resultCollector, config.getTestInvocationListeners()));
            shardConfig.setLogOutput(config.getLogOutput().clone());
            shardConfig.setCommandOptions(config.getCommandOptions().clone());
            // use the same {@link ITargetPreparer}, {@link IDeviceRecovery} etc as original
            // config
            rescheduler.scheduleConfig(shardConfig);
        }
        // clean up original builds
        for (String deviceName : context.getDeviceConfigNames()) {
            config.getDeviceConfigByName(deviceName)
                    .getBuildProvider()
                    .cleanUp(context.getBuildInfo(deviceName));
        }
        return true;
    }

    /**
     * Attempt to shard given {@link IRemoteTest}.
     *
     * @param shardableTests the list of {@link IRemoteTest}s to add to
     * @param test the {@link IRemoteTest} to shard
     * @return <code>true</code> if test was sharded
     */
    private static boolean shardTest(List<IRemoteTest> shardableTests, IRemoteTest test) {
        boolean isSharded = false;
        if (test instanceof IShardableTest) {
            IShardableTest shardableTest = (IShardableTest) test;
            Collection<IRemoteTest> shards = shardableTest.split();
            if (shards != null) {
                shardableTests.addAll(shards);
                isSharded = true;
            }
        }
        if (!isSharded) {
            shardableTests.add(test);
        }
        return isSharded;
    }

    /**
     * Helper to set the Sharded configuration build provider to the {@link ExistingBuildProvider}.
     *
     * @param fromConfig Original configuration
     * @param toConfig cloned configuration recreated from the command line.
     * @param context invocation context
     */
    public static void cloneBuildInfos(
            IConfiguration fromConfig, IConfiguration toConfig, IInvocationContext context) {
        for (String deviceName : context.getDeviceConfigNames()) {
            IBuildInfo toBuild = context.getBuildInfo(deviceName).clone();
            try {
                toConfig.getDeviceConfigByName(deviceName)
                        .addSpecificConfig(
                                new ExistingBuildProvider(
                                        toBuild,
                                        fromConfig
                                                .getDeviceConfigByName(deviceName)
                                                .getBuildProvider()));
            } catch (ConfigurationException e) {
                // Should never happen, no action taken
                CLog.e(e);
            }
        }
    }

    /**
     * Builds the {@link ITestInvocationListener} listeners that will collect the results from all
     * shards. Currently excludes {@link IShardableListener}s.
     */
    private static List<ITestInvocationListener> buildMasterShardListeners(IConfiguration config) {
        List<ITestInvocationListener> newListeners = new ArrayList<ITestInvocationListener>();
        for (ITestInvocationListener l : config.getTestInvocationListeners()) {
            if (!(l instanceof IShardableListener)) {
                newListeners.add(l);
            }
        }
        return newListeners;
    }

    /**
     * Builds the list of {@link ITestInvocationListener}s for each shard. Currently includes any
     * {@link IShardableListener}, plus a single listener that will forward results to the master
     * shard collector.
     */
    private static List<ITestInvocationListener> buildShardListeners(
            ITestInvocationListener resultCollector, List<ITestInvocationListener> origListeners) {
        List<ITestInvocationListener> shardListeners = new ArrayList<ITestInvocationListener>();
        for (ITestInvocationListener l : origListeners) {
            if (l instanceof IShardableListener) {
                shardListeners.add(((IShardableListener) l).clone());
            }
        }
        ShardListener origConfigListener = new ShardListener(resultCollector);
        shardListeners.add(origConfigListener);
        return shardListeners;
    }
}
