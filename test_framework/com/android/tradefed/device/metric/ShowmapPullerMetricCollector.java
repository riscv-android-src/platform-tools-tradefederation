/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base implementation of {@link FilePullerDeviceMetricCollector} that allows pulling the showmap
 * files from the device and collect the metrics from it.
 */
@OptionClass(alias = "showmap-metric-collector")
public class ShowmapPullerMetricCollector extends FilePullerDeviceMetricCollector {

    private static final String PROCESS_NAME_REGEX = "(>>>\\s)(\\S+)(\\s.*<<<)";
    private static final String METRIC_START_END_TEXT = "------";
    private static final String METRIC_VALUE_SEPARATOR = "_";
    private static final String METRIC_UNIT = "bytes";
    private Boolean processFound = false;
    private String processName = null;
    private Map<String, Integer> mGranularInfo = new HashMap<>();
    private Set<String> mProcessObjInfo = new HashSet<>();
    private final Map<String, Integer> mIndexMemoryMap =
            new HashMap<String, Integer>() {
                {
                    put("virtualsize", 0);
                    put("rss", 1);
                    put("pss", 2);
                    put("sharedclean", 3);
                    put("shareddirty", 4);
                    put("privateclean", 5);
                    put("privatedirty", 6);
                    put("swap", 7);
                    put("swappss", 8);
                    put("anonhugepages", 9);
                    put("shmempmdmapped", 10);
                    put("filepmdmapped", 11);
                    put("sharedhugetlb", 12);
                    put("privatehugetlb", 13);
                    // put("flags", 14);
                    put("object", 15);
                }
            };

    @Option(
            name = "showmap-metric-prefix",
            description = "Prefix to be used with the metrics collected from showmap.")
    private String mMetricPrefix = "showmap_granular";

    @Option(
            name = "showmap-process-name",
            description = "Process names to be parsed in showmap file.")
    private Collection<String> mProcessNames = new ArrayList<>();

    /**
     * Process the showmap output file for the additional metrics and add it to final metrics.
     *
     * @param key the option key associated to the file that was pulled from the device.
     * @param metricFile the {@link File} pulled from the device matching the option key.
     * @param data where metrics will be stored.
     */
    @Override
    public void processMetricFile(String key, File metricFile, DeviceMetricData data) {
        String line;
        Boolean metricFound = false;

        if (metricFile != null) {
            try (BufferedReader mBufferReader = new BufferedReader(new FileReader(metricFile))) {
                while ((line = mBufferReader.readLine()) != null) {
                    if (!processFound) {
                        processFound = isProcessFound(line);
                        continue;
                    }
                    metricFound =
                            metricFound
                                    ? computeGranularMetrics(line, processName)
                                    : isMetricParsingStartEnd(line);
                }
            } catch (IOException e) {
                CLog.e("Error parsing showmap granular metrics");
                CLog.e(e);
            } finally {
                writeGranularMetricData(data);
                uploadMetricFile(metricFile);
            }
        }
    }

    @Override
    public void processMetricDirectory(String key, File metricDirectory, DeviceMetricData runData) {
        // Implement if all the files under specific directory have to be post processed.
    }

    /**
     * Extract the showmap file name used for constructing the output metric file
     *
     * @param showmapFileName
     * @return String name of the showmap file name excluding the UUID.
     */
    private String getShowmapFileName(String showmapFileName) {
        // For example return showmap_<test_name>-1_ from
        // showmap_<test_name>-1_13388308985625987330.txt excluding the UID.
        int lastIndex = showmapFileName.lastIndexOf("_");
        if (lastIndex != -1) {
            return showmapFileName.substring(0, lastIndex + 1);
        }
        return showmapFileName;
    }

    /**
     * Computing granular metrics by adding individual memory values for every object and create
     * final metric value
     *
     * @param line
     * @param processName
     */
    private Boolean computeGranularMetrics(String line, String processName) {
        String objectName;
        Integer mGranularValue;
        Integer metricCounter;
        String completeGranularMetric;

        if (isMetricParsingStartEnd(line)) {
            computeObjectsPerProcess(processName);
            processFound = false;
            return false;
        }

        String[] metricLine = line.trim().split("\\s+");
        try {
            objectName = metricLine[mIndexMemoryMap.get("object")];
        } catch (ArrayIndexOutOfBoundsException e) {
            CLog.e("Error parsing granular metrics for %s", processName);
            computeObjectsPerProcess(processName);
            processFound = false;
            return false;
        }

        for (Map.Entry<String, Integer> entry : mIndexMemoryMap.entrySet()) {
            String memName = entry.getKey();
            if (memName.equals("object")) {
                continue;
            }
            try {
                mGranularValue = Integer.parseInt(metricLine[mIndexMemoryMap.get(memName)]);
            } catch (NumberFormatException e) {
                CLog.e("Error parsing granular metrics for %s", processName);
                computeObjectsPerProcess(processName);
                processFound = false;
                return false;
            }
            /**
             * final metric will be of following format
             * showmap_granular_<memory>_bytes_<process>_<object></object>
             * showmap_granular_rss_bytes_system_server_/system/fonts/SourceSansPro-Italic.ttf:104
             */
            completeGranularMetric =
                    String.join(
                            METRIC_VALUE_SEPARATOR,
                            mMetricPrefix,
                            memName,
                            METRIC_UNIT,
                            processName,
                            objectName);
            metricCounter =
                    mGranularInfo.containsKey(completeGranularMetric)
                            ? mGranularInfo.get(completeGranularMetric)
                            : 0;
            mGranularInfo.put(completeGranularMetric, metricCounter + mGranularValue);
        }
        mProcessObjInfo.add(objectName);
        return true;
    }

    /**
     * Append granular metrics to DeviceMetricData object
     *
     * @param data
     */
    private void writeGranularMetricData(DeviceMetricData data) {
        for (Map.Entry<String, Integer> granularData : mGranularInfo.entrySet()) {
            MetricMeasurement.Metric.Builder metricBuilder = MetricMeasurement.Metric.newBuilder();
            metricBuilder.getMeasurementsBuilder().setSingleInt(granularData.getValue());
            data.addMetric(
                    String.format("%s", granularData.getKey()),
                    metricBuilder.setType(MetricMeasurement.DataType.RAW));
        }
    }

    /**
     * Uploads showmap text file to artifacts
     *
     * @param uploadFile
     */
    private void uploadMetricFile(File uploadFile) {
        try (InputStreamSource source = new FileInputStreamSource(uploadFile, true)) {
            testLog(getShowmapFileName(uploadFile.getName()), LogDataType.TEXT, source);
        }
    }

    /**
     * Returns if line contains '------' text
     *
     * @param line
     * @return true or false
     */
    private Boolean isMetricParsingStartEnd(String line) {
        if (line.contains(METRIC_START_END_TEXT)) {
            return true;
        }
        return false;
    }

    /**
     * Returns if particular process needs to be parsed
     *
     * @param line
     * @return true or false
     */
    private Boolean isProcessFound(String line) {
        Boolean psResult;
        Pattern psPattern = Pattern.compile(PROCESS_NAME_REGEX);
        Matcher psMatcher = psPattern.matcher(line);
        if (psMatcher.find()) {
            processName = psMatcher.group(2);
            psResult = mProcessNames.isEmpty() || mProcessNames.contains(processName);
            return psResult;
        }
        return false;
    }

    /**
     * Counts total no. of unique objects per process showmap_granular_<process>_total_object_count
     *
     * @param processName
     */
    private void computeObjectsPerProcess(String processName) {
        String objCounterMetric =
                String.join(
                        METRIC_VALUE_SEPARATOR, mMetricPrefix, processName, "total_object_count");
        if (mProcessObjInfo.size() > 0) {
            mGranularInfo.put(objCounterMetric, mProcessObjInfo.size());
            mProcessObjInfo.clear();
        }
    }
}
