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

package com.android.tradefed.config;

import static org.junit.Assert.assertTrue;

import com.android.tradefed.device.DeviceManager;
import com.android.tradefed.util.FileUtil;

import org.junit.Test;
import org.kxml2.io.KXmlSerializer;

import java.io.File;
import java.io.PrintWriter;

/** Unit tests for {@link ConfigurationUtil} */
public class ConfigurationUtilTest {

    private static final String DEVICE_MANAGER_TYPE_NAME = "device_manager";

    /**
     * Test {@link ConfigurationUtil#dumpClassToXml(KXmlSerializer, String, Object)} to create a
     * dump of a configuration.
     */
    @Test
    public void testDumpClassToXml() throws Throwable {
        File tmpXml = FileUtil.createTempFile("global_config", ".xml");
        try {
            PrintWriter output = new PrintWriter(tmpXml);
            KXmlSerializer serializer = new KXmlSerializer();
            serializer.setOutput(output);
            serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
            serializer.startDocument("UTF-8", null);
            serializer.startTag(null, ConfigurationUtil.CONFIGURATION_NAME);

            DeviceManager deviceManager = new DeviceManager();
            ConfigurationUtil.dumpClassToXml(serializer, DEVICE_MANAGER_TYPE_NAME, deviceManager);

            serializer.endTag(null, ConfigurationUtil.CONFIGURATION_NAME);
            serializer.endDocument();

            // Read the dump XML file, make sure configurations can be loaded.
            String content = FileUtil.readStringFromFile(tmpXml);
            assertTrue(content.length() > 100);
            assertTrue(content.contains("<configuration>"));
            assertTrue(content.contains("<option name=\"adb-path\" value=\"adb\" />"));
            assertTrue(
                    content.contains(
                            "<device_manager class=\"com.android.tradefed.device.DeviceManager\">"));
        } finally {
            FileUtil.deleteFile(tmpXml);
        }
    }
}
