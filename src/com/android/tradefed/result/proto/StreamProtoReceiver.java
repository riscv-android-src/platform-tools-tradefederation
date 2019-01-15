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
package com.android.tradefed.result.proto;

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.proto.TestRecordProto.TestRecord;
import com.android.tradefed.util.StreamUtil;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A receiver that translates proto TestRecord received into Tradefed events.
 */
public class StreamProtoReceiver implements Closeable {

    private static final int DEFAULT_AVAILABLE_PORT = 0;

    private EventReceiverThread mEventReceiver;
    private ITestInvocationListener mListener;
    private ProtoResultParser mParser;
    private Throwable mError;

    /**
     * Ctor.
     *
     * @param listener the {@link ITestInvocationListener} where to report the results.
     * @param reportInvocation Whether or not to report the invocation level events.
     * @throws IOException
     */
    public StreamProtoReceiver(ITestInvocationListener listener, boolean reportInvocation)
            throws IOException {
        this(listener, reportInvocation, true);
    }

    /**
     * Ctor.
     *
     * @param listener the {@link ITestInvocationListener} where to report the results.
     * @param reportInvocation Whether or not to report the invocation level events.
     * @param quietParsing Whether or not to let the parser log debug information.
     * @throws IOException
     */
    public StreamProtoReceiver(
            ITestInvocationListener listener, boolean reportInvocation, boolean quietParsing)
            throws IOException {
        this(listener, reportInvocation, quietParsing, "subprocess-");
    }

    /**
     * Ctor.
     *
     * @param listener the {@link ITestInvocationListener} where to report the results.
     * @param reportInvocation Whether or not to report the invocation level events.
     * @param quietParsing Whether or not to let the parser log debug information.
     * @param logNamePrefix The prefix for file logged through the parser.
     * @throws IOException
     */
    public StreamProtoReceiver(
            ITestInvocationListener listener,
            boolean reportInvocation,
            boolean quietParsing,
            String logNamePrefix)
            throws IOException {
        mListener = listener;
        mParser = new ProtoResultParser(mListener, reportInvocation, logNamePrefix);
        mParser.setQuiet(quietParsing);
        mEventReceiver = new EventReceiverThread();
        mEventReceiver.start();
    }

    /** Internal receiver thread class with a socket. */
    private class EventReceiverThread extends Thread {
        private ServerSocket mSocket;
        private CountDownLatch mCountDown;

        public EventReceiverThread() throws IOException {
            super("ProtoEventReceiverThread");
            mSocket = new ServerSocket(DEFAULT_AVAILABLE_PORT);
            mCountDown = new CountDownLatch(1);
        }

        protected int getLocalPort() {
            return mSocket.getLocalPort();
        }

        protected CountDownLatch getCountDown() {
            return mCountDown;
        }

        public void cancel() throws IOException {
            if (mSocket != null) {
                mSocket.close();
            }
        }

        @Override
        public void run() {
            Socket client = null;
            BufferedReader in = null;
            try {
                client = mSocket.accept();
                TestRecord received = null;
                while ((received = TestRecord.parseDelimitedFrom(client.getInputStream()))
                        != null) {
                    parse(received);
                }
            } catch (IOException e) {
                CLog.e(e);
            } finally {
                StreamUtil.close(in);
                mCountDown.countDown();
            }
            CLog.d("ProtoEventReceiverThread done.");
        }
    }

    /** Returns the socket receiver that was open. -1 if none. */
    public int getSocketServerPort() {
        if (mEventReceiver != null) {
            return mEventReceiver.getLocalPort();
        }
        return -1;
    }

    /** Returns the error caugh in the receiver thread. If none it will return null. */
    public Throwable getError() {
        return mError;
    }

    @Override
    public void close() throws IOException {
        if (mEventReceiver != null) {
            mEventReceiver.cancel();
        }
    }

    public boolean joinReceiver(long millis) {
        if (mEventReceiver != null) {
            try {
                CLog.i("Waiting for events to finish being processed.");
                if (!mEventReceiver.getCountDown().await(millis, TimeUnit.MILLISECONDS)) {
                    CLog.e("Event receiver thread did not complete. Some events may be missing.");
                    return false;
                }
            } catch (InterruptedException e) {
                CLog.e(e);
                throw new RuntimeException(e);
            }
        }
        return true;
    }

    private void parse(TestRecord receivedRecord) {
        try {
            mParser.processNewProto(receivedRecord);
        } catch (Throwable e) {
            CLog.e(e);
            mError = e;
            throw e;
        }
    }
}
