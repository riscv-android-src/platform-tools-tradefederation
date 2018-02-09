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
package com.android.tradefed.device.metric;

import com.android.loganalysis.item.DumpsysProcessMeminfoItem;
import com.android.loganalysis.parser.DumpsysProcessMeminfoParser;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link ScheduledDeviceMetricCollector} to measure peak memory usage of specified processes.
 * Collects PSS and USS (private dirty) memory usage values from dumpsys meminfo. The result will be
 * reported as a test run metric with key in the form of PSS#ProcName[#DeviceNum], in KB.
 */
public class ProcessMaxMemoryCollector extends ScheduledDeviceMetricCollector {

    @Option(
        name = "memory-usage-process-name",
        description = "Process names (from `dumpsys meminfo`) to measure memory usage for"
    )
    private List<String> mProcessNames = new ArrayList<>();

    private class DeviceMemoryData {
        /** Peak PSS per process */
        private Map<String, Long> mProcPss = new HashMap<>();
        /** Peak USS per process */
        private Map<String, Long> mProcUss = new HashMap<>();
    }

    // Memory usage data per device
    private Map<ITestDevice, DeviceMemoryData> mMemoryData = new HashMap<>();

    @Override
    void onStart(DeviceMetricData runData) {
        for (ITestDevice device : getDevices()) {
            mMemoryData.put(device, new DeviceMemoryData());
        }
    }

    @Override
    void collect(ITestDevice device, DeviceMetricData runData) throws InterruptedException {
        try {
            Map<String, Long> procPss = mMemoryData.get(device).mProcPss;
            Map<String, Long> procUss = mMemoryData.get(device).mProcUss;
            for (String proc : mProcessNames) {
                String dumpResult = device.executeShellCommand("dumpsys meminfo --checkin " + proc);
                if (dumpResult.startsWith("No process found")) {
                    // process not found, skip
                    continue;
                }
                DumpsysProcessMeminfoItem item =
                        new DumpsysProcessMeminfoParser()
                                .parse(Arrays.asList(dumpResult.split("\n")));
                Long pss =
                        item.get(DumpsysProcessMeminfoItem.TOTAL)
                                .get(DumpsysProcessMeminfoItem.PSS);
                Long uss =
                        item.get(DumpsysProcessMeminfoItem.TOTAL)
                                .get(DumpsysProcessMeminfoItem.PRIVATE_DIRTY);
                if (pss == null || uss == null) {
                    CLog.e("Error parsing meminfo output: " + dumpResult);
                    continue;
                }
                if (procPss.getOrDefault(proc, 0L) < pss) {
                    procPss.put(proc, pss);
                }
                if (procUss.getOrDefault(proc, 0L) < uss) {
                    procUss.put(proc, uss);
                }
            }
        } catch (DeviceNotAvailableException e) {
            CLog.e(e);
        }
    }

    @Override
    void onEnd(DeviceMetricData runData) {
        for (ITestDevice device : getDevices()) {
            Map<String, Long> procPss = mMemoryData.get(device).mProcPss;
            Map<String, Long> procUss = mMemoryData.get(device).mProcUss;
            for (Map.Entry<String, Long> pss : procPss.entrySet()) {
                runData.addStringMetricForDevice(
                        device, "PSS#" + pss.getKey(), pss.getValue().toString());
            }
            for (Map.Entry<String, Long> uss : procUss.entrySet()) {
                runData.addStringMetricForDevice(
                        device, "USS#" + uss.getKey(), uss.getValue().toString());
            }
        }
    }
}
