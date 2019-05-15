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
package com.android.tradefed.util.statsd;

import com.android.os.StatsLog.ConfigMetricsReport;
import com.android.os.StatsLog.ConfigMetricsReportList;
import com.android.os.StatsLog.EventMetricData;
import com.android.os.StatsLog.StatsLogReport;
import com.android.tradefed.device.CollectingByteOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.InputStreamSource;

import com.google.protobuf.InvalidProtocolBufferException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Utility class for pulling metrics from pushed statsd configurations. */
public class MetricUtil {
    static final String DUMP_REPORT_CMD_TEMPLATE =
            "cmd stats dump-report %s --include_current_bucket --proto";

    /** Get statsd event metrics data from the device using the statsd config id. */
    public static List<EventMetricData> getEventMetricData(ITestDevice device, long configId)
            throws DeviceNotAvailableException {
        ConfigMetricsReportList reports = getReportList(device, configId);
        if (reports.getReportsList().isEmpty()) {
            CLog.d("No stats report collected.");
            return new ArrayList<EventMetricData>();
        }
        ConfigMetricsReport report = reports.getReports(0);
        List<EventMetricData> data = new ArrayList<>();
        for (StatsLogReport metric : report.getMetricsList()) {
            data.addAll(metric.getEventMetrics().getDataList());
        }
        data.sort(Comparator.comparing(EventMetricData::getElapsedTimestampNanos));

        CLog.d("Received EventMetricDataList as following:\n");
        for (EventMetricData d : data) {
            CLog.d("Atom at %d:\n%s", d.getElapsedTimestampNanos(), d.getAtom().toString());
        }
        return data;
    }

    /** Get Statsd report as a byte stream source */
    public static InputStreamSource getReportByteStream(ITestDevice device, long configId)
            throws DeviceNotAvailableException {
        return new ByteArrayInputStreamSource(getReportByteArray(device, configId));
    }

    /** Get the report list proto from the device for the given {@code configId}. */
    private static ConfigMetricsReportList getReportList(ITestDevice device, long configId)
            throws DeviceNotAvailableException {
        try {
            byte[] output = getReportByteArray(device, configId);
            return ConfigMetricsReportList.parser().parseFrom(output);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] getReportByteArray(ITestDevice device, long configId)
            throws DeviceNotAvailableException {
        final CollectingByteOutputReceiver receiver = new CollectingByteOutputReceiver();
        CLog.d(
                "Dumping stats report with command: "
                        + String.format(DUMP_REPORT_CMD_TEMPLATE, String.valueOf(configId)));
        device.executeShellCommand(
                String.format(DUMP_REPORT_CMD_TEMPLATE, String.valueOf(configId)), receiver);
        return receiver.getOutput();
    }


}
