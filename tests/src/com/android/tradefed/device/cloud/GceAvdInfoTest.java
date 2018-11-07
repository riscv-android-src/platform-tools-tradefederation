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
package com.android.tradefed.device.cloud;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import com.android.tradefed.targetprep.TargetSetupError;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link GceAvdInfo} */
@RunWith(JUnit4.class)
public class GceAvdInfoTest {

    @Test
    public void testValidGceJsonParsing() throws Exception {
        String valid =
                " {\n"
                        + "    \"data\": {\n"
                        + "      \"devices\": [\n"
                        + "        {\n"
                        + "          \"ip\": \"104.154.62.236\",\n"
                        + "          \"instance_name\": \"gce-x86-phone-userdebug-2299773-22cf\"\n"
                        + "        }\n"
                        + "      ]\n"
                        + "    },\n"
                        + "    \"errors\": [],\n"
                        + "    \"command\": \"create\",\n"
                        + "    \"status\": \"SUCCESS\"\n"
                        + "  }";
        GceAvdInfo avd = GceAvdInfo.parseGceInfoFromString(valid, null, 5555);
        assertNotNull(avd);
        assertEquals(avd.hostAndPort().getHostText(), "104.154.62.236");
        assertEquals(avd.instanceName(), "gce-x86-phone-userdebug-2299773-22cf");
    }

    @Test
    public void testNullStringJsonParsing() throws Exception {
        GceAvdInfo avd = GceAvdInfo.parseGceInfoFromString(null, null, 5555);
        assertNull(avd);
    }

    @Test
    public void testEmptyStringJsonParsing() throws Exception {
        assertNull(GceAvdInfo.parseGceInfoFromString(new String(), null, 5555));
    }

    @Test
    public void testMultipleGceJsonParsing() throws Exception {
        String multipleInstances =
                " {\n"
                        + "    \"data\": {\n"
                        + "      \"devices\": [\n"
                        + "        {\n"
                        + "          \"ip\": \"104.154.62.236\",\n"
                        + "          \"instance_name\": \"gce-x86-phone-userdebug-2299773-22ecc\"\n"
                        + "        },\n"
                        + "       {\n"
                        + "          \"ip\": \"104.154.62.236\",\n"
                        + "          \"instance_name\": \"gce-x86-phone-userdebug-2299773-22ecc\"\n"
                        + "        }\n"
                        + "      ]\n"
                        + "    },\n"
                        + "    \"errors\": [],\n"
                        + "    \"command\": \"create\",\n"
                        + "    \"status\": \"SUCCESS\"\n"
                        + "  }";
        try {
            GceAvdInfo.parseGceInfoFromString(multipleInstances, null, 5555);
            fail("A TargetSetupError should have been thrown.");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    @Test
    public void testInvalidJsonParsing() throws Exception {
        String invalidJson = "bad_json";
        try {
            GceAvdInfo.parseGceInfoFromString(invalidJson, null, 5555);
            fail("A TargetSetupError should have been thrown.");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    @Test
    public void testMissingGceJsonParsing() throws Exception {
        String missingInstance =
                " {\n"
                        + "    \"data\": {\n"
                        + "      \"devices\": [\n"
                        + "      ]\n"
                        + "    },\n"
                        + "    \"errors\": [],\n"
                        + "    \"command\": \"create\",\n"
                        + "    \"status\": \"SUCCESS\"\n"
                        + "  }";
        try {
            GceAvdInfo.parseGceInfoFromString(missingInstance, null, 5555);
            fail("A TargetSetupError should have been thrown.");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    /**
     * In case of failure to boot in expected time, we need to parse the error to get the instance
     * name and stop it.
     *
     * @throws Exception
     */
    @Test
    public void testValidGceJsonParsingFail() throws Exception {
        String validFail =
                " {\n"
                        + "    \"data\": {\n"
                        + "      \"devices_failing_boot\": [\n"
                        + "        {\n"
                        + "          \"ip\": \"104.154.62.236\",\n"
                        + "          \"instance_name\": \"gce-x86-phone-userdebug-2299773-22ecc\"\n"
                        + "        }\n"
                        + "      ]\n"
                        + "    },\n"
                        + "    \"errors\": [],\n"
                        + "    \"command\": \"create\",\n"
                        + "    \"status\": \"FAIL\"\n"
                        + "  }";
        GceAvdInfo avd = GceAvdInfo.parseGceInfoFromString(validFail, null, 5555);
        assertNotNull(avd);
        assertEquals(avd.hostAndPort().getHostText(), "104.154.62.236");
        assertEquals(avd.instanceName(), "gce-x86-phone-userdebug-2299773-22ecc");
    }

    /**
     * On a quota error No GceAvd information is created because the instance was not created.
     *
     * @throws Exception
     */
    @Test
    public void testValidGceJsonParsingFailQuota() throws Exception {
        String validError =
                " {\n"
                        + "    \"data\": {},\n"
                        + "    \"errors\": [\n"
                        + "\"Get operation state failed, errors: [{u'message': u\\\"Quota 'CPUS' "
                        + "exceeded.  Limit: 500.0\\\", u'code': u'QUOTA_EXCEEDED'}]\"\n"
                        + "],\n"
                        + "    \"command\": \"create\",\n"
                        + "    \"status\": \"FAIL\"\n"
                        + "  }";
        try {
            GceAvdInfo.parseGceInfoFromString(validError, null, 5555);
            fail("A TargetSetupError should have been thrown.");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    /**
     * In case of failure to boot in expected time, we need to parse the error to get the instance
     * name and stop it.
     *
     * @throws Exception
     */
    @Test
    public void testParseJson_Boot_Fail() throws Exception {
        String validFail =
                " {\n"
                        + "    \"data\": {\n"
                        + "      \"devices_failing_boot\": [\n"
                        + "        {\n"
                        + "          \"ip\": \"104.154.62.236\",\n"
                        + "          \"instance_name\": \"gce-x86-phone-userdebug-2299773-22ec\"\n"
                        + "        }\n"
                        + "      ]\n"
                        + "    },\n"
                        + "    \"errors\": [\"device did not boot\"],\n"
                        + "    \"command\": \"create\",\n"
                        + "    \"status\": \"BOOT_FAIL\"\n"
                        + "  }";
        GceAvdInfo avd = GceAvdInfo.parseGceInfoFromString(validFail, null, 5555);
        assertNotNull(avd);
        assertEquals(avd.hostAndPort().getHostText(), "104.154.62.236");
        assertEquals(avd.instanceName(), "gce-x86-phone-userdebug-2299773-22ec");
        assertEquals(GceAvdInfo.GceStatus.BOOT_FAIL, avd.getStatus());
    }

    /**
     * In case of failure to start the instance if no 'devices_failing_boot' is available avoid
     * parsing the instance.
     */
    @Test
    public void testParseJson_fail_error() throws Exception {
        String validFail =
                " {\n"
                        + "    \"data\": {},\n"
                        + "    \"errors\": [\"HttpError 403 when requesting\"],\n"
                        + "    \"command\": \"create\",\n"
                        + "    \"status\": \"FAIL\"\n"
                        + "  }";
        try {
            GceAvdInfo.parseGceInfoFromString(validFail, null, 5555);
            fail("A TargetSetupError should have been thrown.");
        } catch (TargetSetupError e) {
            // expected
        }
    }
}
