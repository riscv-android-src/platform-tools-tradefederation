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
package com.android.tradefed.invoker.shard;

import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.ConfigurationFactory;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationFactory;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.IRescheduler;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.IShardableTest;
import com.android.tradefed.testtype.IStrictShardableTest;
import com.android.tradefed.util.QuotationAwareTokenizer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Sharding strategy to create strict shards that do not report together, */
public class StrictShardHelper implements IShardHelper {

    /** {@inheritDoc} */
    @Override
    public boolean shardConfig(
            IConfiguration config, IInvocationContext context, IRescheduler rescheduler) {
        Integer shardCount = config.getCommandOptions().getShardCount();
        Integer shardIndex = config.getCommandOptions().getShardIndex();
        if (shardCount == null) {
            // if no number of shard was requested.
            return false;
        }

        // Split tests in place, without actually sharding.
        if (shardIndex != null) {
            if (!config.getCommandOptions().shouldUseTfSharding()) {
                updateConfigIfSharded(config, shardCount, shardIndex);
            } else {
                List<IRemoteTest> listAllTests = getAllTests(config, shardCount);
                config.setTests(splitTests(listAllTests, shardCount, shardIndex));
            }
            return false;
        }

        List<IRemoteTest> listAllTests = getAllTests(config, shardCount);
        // Schedules shard configs.
        // TODO: merge this behavior with {@link ShardHelper} since when no shard-index is specified
        // we are basically doing local sharding.
        for (int i = 0; i < shardCount; i++) {
            IConfiguration shardConfig = deepCloneConfig(config);

            // Temporary flag to default to old logic during testing of new one.
            if (!config.getCommandOptions().shouldUseTfSharding()) {
                updateConfigIfSharded(shardConfig, shardCount, i);
            } else {
                config.setTests(splitTests(listAllTests, shardCount, i));
            }
            ShardBuildCloner.cloneBuildInfos(config, shardConfig, context);

            shardConfig.getCommandOptions().setShardCount(shardCount);
            shardConfig.getCommandOptions().setShardIndex(i);
            rescheduler.scheduleConfig(shardConfig);
        }
        return true;
    }

    // TODO: Retire IStrictShardableTest for IShardableTest and have TF balance the list of tests.
    private void updateConfigIfSharded(IConfiguration config, int shardCount, int shardIndex) {
        List<IRemoteTest> testShards = new ArrayList<>();
        for (IRemoteTest test : config.getTests()) {
            if (!(test instanceof IStrictShardableTest)) {
                CLog.w(
                        "%s is not shardable; the whole test will run in shard 0",
                        test.getClass().getName());
                if (shardIndex == 0) {
                    testShards.add(test);
                }
                continue;
            }
            IRemoteTest testShard =
                    ((IStrictShardableTest) test).getTestShard(shardCount, shardIndex);
            testShards.add(testShard);
        }
        config.setTests(testShards);
    }

    /**
     * Helper to return the full list of {@link IRemoteTest} based on {@link IShardableTest} split.
     *
     * @param config the {@link IConfiguration} describing the invocation.
     * @param shardCount the shard count hint to be provided to some tests.
     * @return the list of all {@link IRemoteTest}.
     */
    private List<IRemoteTest> getAllTests(IConfiguration config, Integer shardCount) {
        List<IRemoteTest> allTests = new ArrayList<>();
        for (IRemoteTest test : config.getTests()) {
            if (test instanceof IShardableTest) {
                Collection<IRemoteTest> subTests = ((IShardableTest) test).split(shardCount);
                if (subTests == null) {
                    allTests.add(test);
                } else {
                    allTests.addAll(subTests);
                }
            }
        }
        return allTests;
    }

    /**
     * Split the list of tests to run however the implementation see fit. Sharding needs to be
     * consistent. It is acceptable to return an empty list if no tests can be run in the shard.
     *
     * <p>Implement this in order to provide a test suite specific sharding. The default
     * implementation strictly split by number of tests which is not always optimal. TODO: improve
     * the splitting criteria.
     *
     * @param fullList the initial full list of {@link IRemoteTest} containing all the tests that
     *     need to run.
     * @param shardCount the total number of shard that need to run.
     * @param shardIndex the index of the current shard that needs to run.
     * @return a list of {@link IRemoteTest} that need to run in the current shard.
     */
    private List<IRemoteTest> splitTests(
            List<IRemoteTest> fullList, int shardCount, int shardIndex) {
        if (shardCount == 1) {
            // Not sharded
            return fullList;
        }
        if (shardIndex >= fullList.size()) {
            // Return empty list when we don't have enough tests for all the shards.
            return new ArrayList<IRemoteTest>();
        }
        int numPerShard = (int) Math.ceil(fullList.size() / (float) shardCount);
        if (shardIndex == shardCount - 1) {
            // last shard take everything remaining.
            return fullList.subList(shardIndex * numPerShard, fullList.size());
        }
        return fullList.subList(shardIndex * numPerShard, numPerShard + (shardIndex * numPerShard));
    }

    /** Clone a {@link IConfiguration} using the original command line. */
    private IConfiguration deepCloneConfig(IConfiguration origConfig) {
        IConfiguration shardConfig = null;
        // Create a deep copy of the configuration.
        try {
            shardConfig =
                    getConfigFactory()
                            .createConfigurationFromArgs(
                                    QuotationAwareTokenizer.tokenizeLine(
                                            origConfig.getCommandLine()));
        } catch (ConfigurationException e) {
            // This must not happen.
            throw new RuntimeException("failed to deep copy a configuration", e);
        }
        return shardConfig;
    }

    /**
     * Factory method for getting a reference to the {@link IConfigurationFactory}
     *
     * @return the {@link IConfigurationFactory} to use
     */
    protected IConfigurationFactory getConfigFactory() {
        return ConfigurationFactory.getInstance();
    }
}
