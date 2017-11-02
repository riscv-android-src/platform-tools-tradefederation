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
package com.android.tradefed.device.metric;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** A {@link ScheduledDeviceMetricCollector} to collect memory dumps at regular intervals. */
public final class MeminfoMetricCollector extends ScheduledDeviceMetricCollector {
    MeminfoMetricCollector() {
        setTag("meminfo");
    }

    @Override
    public void collect(DeviceMetricData runData) throws InterruptedException {
        String fileSuffix =
                new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(new Date());
        try {
            CLog.i("Running meminfo...");
            File outputFile =
                    new File(
                            String.format(
                                    "%s/compact-meminfo-%s.txt",
                                    Files.createTempDir(), fileSuffix));
            saveProcessOutput("dumpsys meminfo -c -S", outputFile);
            runData.addStringMetric(
                    Files.getNameWithoutExtension(outputFile.getName()), outputFile.getPath());
        } catch (DeviceNotAvailableException | IOException e) {
            CLog.e(e);
        }
    }
}
