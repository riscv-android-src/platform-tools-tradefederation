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
package com.android.tradefed.command.remote;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;

import com.android.tradefed.command.ICommandScheduler;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.util.RunUtil;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;

/** Unit tests for {@link RemoteManager}. */
public class RemoteManagerTest {

    private static final long SHORT_WAIT_TIME_MS = 200;

    private RemoteManager mRemoteManager;
    private ICommandScheduler mMockScheduler;
    private IDeviceManager mMockDeviceManager;

    @Before
    public void setUp() {
        mMockScheduler = Mockito.mock(ICommandScheduler.class);
        mMockDeviceManager = Mockito.mock(IDeviceManager.class);
        mRemoteManager = new RemoteManager(mMockDeviceManager, mMockScheduler);
    }

    /**
     * Test inputing an invalid string (non-json) to the RemoteManager. It should be correctly
     * rejected.
     */
    @Test
    public void testProcessClientOperations_invalidAction() throws IOException {
        String buf = "test\n";
        InputStream data = new ByteArrayInputStream(buf.getBytes());
        BufferedReader in = new BufferedReader(new InputStreamReader(data));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintWriter pw = new PrintWriter(out);
        mRemoteManager.processClientOperations(in, pw);
        pw.flush();
        assertEquals(
                "{\"error\":\"Failed to handle remote command: "
                        + "com.android.tradefed.command.remote.RemoteException: "
                        + "Value test of type java.lang.String cannot be converted to JSONObject\"}\n",
                out.toString());
    }

    /**
     * Test sending a start handover command on a port and verify the command scheduler is notified.
     */
    @Test
    public void testProcessClientOperations_initHandover() throws IOException {
        String buf = "{version=\"8\", type=\"START_HANDOVER\", port=\"5555\"}";
        InputStream data = new ByteArrayInputStream(buf.getBytes());
        BufferedReader in = new BufferedReader(new InputStreamReader(data));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintWriter pw = new PrintWriter(out);
        mRemoteManager.processClientOperations(in, pw);
        pw.flush();
        // ack is received without error.
        assertEquals("{}\n", out.toString());
        // wait a little bit to let the postOperation thread to run.
        RunUtil.getDefault().sleep(SHORT_WAIT_TIME_MS);
        // handover was sent to the scheduler.
        verify(mMockScheduler).handoverShutdown(5555);
    }
}
