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

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;

/** A {@link ScheduledDeviceMetricCollector} to collect free page counts at regular intervals. */
public class PagetypeInfoMetricCollector extends ScheduledDeviceMetricCollector {
    public PagetypeInfoMetricCollector() {
        setTag("pagetypeinfo");
    }

    @Override
    void collect(DeviceMetricData runData) throws InterruptedException {
        try {
            CLog.i("Running pagetype info collector...");
            File outputFile =
                    new File(
                            String.format(
                                    "%s/pagetypeinfo-%s.txt", createTempDir(), getFileSuffix()));
            saveProcessOutput("cat /proc/pagetypeinfo", outputFile);
            runData.addStringMetric(
                    Files.getNameWithoutExtension(outputFile.getName()), outputFile.getPath());
        } catch (DeviceNotAvailableException | IOException e) {
            CLog.e(e);
        }
    }
}
