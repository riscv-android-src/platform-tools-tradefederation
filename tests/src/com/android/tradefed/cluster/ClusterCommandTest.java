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
package com.android.tradefed.cluster;

import org.junit.Assert;
import org.json.JSONObject;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ClusterCommandTest {
    @Test
    public void testFromJsonWithAssignedAttemptId() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("request_id", "i123");
        json.put("command_id", "c123");
        json.put("task_id", "t123");
        json.put("command_line", "command line");
        json.put("attempt_id", "a123");

        ClusterCommand command = ClusterCommand.fromJson(json);

        Assert.assertEquals("i123", command.getRequestId());
        Assert.assertEquals("c123", command.getCommandId());
        Assert.assertEquals("t123", command.getTaskId());
        Assert.assertEquals("command line", command.getCommandLine());
        Assert.assertEquals("a123", command.getAttemptId());
    }

    @Test
    public void testFromJsonWithoutAssignedAttemptId() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("request_id", "i123");
        json.put("command_id", "c123");
        json.put("task_id", "t123");
        json.put("command_line", "command line");

        ClusterCommand command = ClusterCommand.fromJson(json);

        Assert.assertEquals("i123", command.getRequestId());
        Assert.assertEquals("c123", command.getCommandId());
        Assert.assertEquals("t123", command.getTaskId());
        Assert.assertEquals("command line", command.getCommandLine());
        String UUIDPattern =
                "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
        Assert.assertTrue(command.getAttemptId().matches(UUIDPattern));
    }
}
