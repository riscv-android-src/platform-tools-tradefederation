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
package com.android.tradefed.targetprep;

import static org.junit.Assert.*;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.FileUtil;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;

/** Unit tests for {@link BaseTargetPreparer}. */
@RunWith(JUnit4.class)
public class BaseTargetPreparerTest {

    private static class DisabledBaseTargetPreparer extends BaseTargetPreparer {

        DisabledBaseTargetPreparer() {
            setDisable(true);
        }

        @Override
        public void setUp(ITestDevice device, IBuildInfo buildInfo)
                throws TargetSetupError, BuildError, DeviceNotAvailableException {
            // Ignore
        }
    }

    @Test
    public void testDisabledByDefault() throws Exception {
        DisabledBaseTargetPreparer preparer = new DisabledBaseTargetPreparer();
        assertTrue(preparer.isDisabled());
        IConfiguration config = new Configuration("test", "test");
        config.setTargetPreparer(preparer);
        File configFile = FileUtil.createTempFile("base-target-prep-config", ".xml");
        try (PrintWriter pw = new PrintWriter(configFile)) {
            config.dumpXml(pw, new ArrayList<>(), false, false);
            String value = FileUtil.readStringFromFile(configFile);
            assertTrue(value.contains("<option name=\"disable\" value=\"true\" />"));
            // Disable-tear-down was not modified so it should not appear.
            assertFalse(value.contains("disable-tear-down"));
        } finally {
            FileUtil.deleteFile(configFile);
        }
    }
}
