/*
 * Copyright (C) 2010 The Android Open Source Project
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
package com.android.tradefed.log;

import com.android.ddmlib.Log;
import com.android.ddmlib.Log.LogLevel;

import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

/** Unit tests for {@link LogRegistry}. */
@RunWith(JUnit4.class)
public class LogRegistryTest {

    private static final String LOG_TAG = "LogRegistryTest";
    private static final long MAX_HISTORY_SIZE = 5;

    private LogRegistry mLogRegistry;
    private ThreadGroup mStubThreadGroup;

    @Before
    public void setUp() throws Exception {
        mStubThreadGroup = new ThreadGroup("LogRegistryTest");
        mLogRegistry =
                new LogRegistry() {
                    // override thread group to avoid conflict with the "real" LogRegistry and the
                    // logger in use for this test run
                    @Override
                    ThreadGroup getCurrentThreadGroup() {
                        return mStubThreadGroup;
                    }

                    @Override
                    public void saveGlobalLog() {
                        // empty on purpose, avoid leaving logs that we can't clean.
                    }

                    @Override
                    long getMaxHistorySize() {
                        return MAX_HISTORY_SIZE;
                    }
                };
    }

    @After
    public void tearDown() throws Exception {
        mLogRegistry.closeAndRemoveAllLogs();
    }

    /**
     * Tests that {@link LogRegistry#getLogger} returns the logger that was previously registered.
     */
    @Test
    public void testGetLogger() {
        StdoutLogger stdoutLogger = new StdoutLogger();
        mLogRegistry.registerLogger(stdoutLogger);

        ILeveledLogOutput returnedLogger = mLogRegistry.getLogger();
        Assert.assertEquals(stdoutLogger, returnedLogger);
        mLogRegistry.unregisterLogger();
    }

    /**
     * Tests that {@link LogRegistry#printLog} calls into the underlying logger's printLog method
     * when the logging level is appropriate for printing.
     */
    @Test
    public void testPrintLog_sameLogLevel() {
        String testMessage = "This is a test message.";
        ILeveledLogOutput mockLogger = EasyMock.createMock(ILeveledLogOutput.class);
        mLogRegistry.registerLogger(mockLogger);

        EasyMock.expect(mockLogger.getLogLevel()).andReturn(LogLevel.VERBOSE);
        mockLogger.printLog(LogLevel.VERBOSE, LOG_TAG, testMessage);

        EasyMock.replay(mockLogger);
        mLogRegistry.printLog(LogLevel.VERBOSE, LOG_TAG, testMessage);
        mLogRegistry.unregisterLogger();
    }

    /**
     * Tests that {@link LogRegistry#printLog} does not call into the underlying logger's printLog
     * method when the logging level is lower than necessary to log.
     */
    @Test
    public void testPrintLog_lowerLogLevel() {
        String testMessage = "This is a test message.";
        ILeveledLogOutput mockLogger = EasyMock.createMock(ILeveledLogOutput.class);
        mLogRegistry.registerLogger(mockLogger);

        // Setting LogLevel == ERROR will let everything print
        EasyMock.expect(mockLogger.getLogLevel()).andReturn(LogLevel.ERROR);

        EasyMock.replay(mockLogger);
        mLogRegistry.printLog(LogLevel.VERBOSE, LOG_TAG, testMessage);
        mLogRegistry.unregisterLogger();
    }

    /**
     * Tests for ensuring new threads spawned without an explicit ThreadGroup will inherit the same
     * logger as the parent's logger.
     */
    @Test
    public void testThreadedLogging() {
        final String testMessage = "Another test message!";
        final ILeveledLogOutput mockLogger = EasyMock.createMock(ILeveledLogOutput.class);

        // Simple class that will run in a thread, simulating a new thread spawed inside an Invocation
        class SecondThread implements Runnable {
            @Override
            public void run() {
                Log.e(LOG_TAG, testMessage);
            }
        }
        // Simple class that will run in a thread, simulating a new Invocation
        class FirstThread implements Runnable {
            @Override
            public void run() {
                mLogRegistry.registerLogger(mockLogger);
                Log.v(LOG_TAG, testMessage);
                Thread secondThread = new Thread(new SecondThread());  // no explicit ThreadGroup
                secondThread.start();
                try {
                    secondThread.join();  // threaded, but force serialization for testing
                }
                catch (InterruptedException ie) {
                    Assert.fail("Thread was unexpectedly interrupted.");
                }
                finally {
                    mLogRegistry.unregisterLogger();
                }
            }
        }

        // first thread calls
        EasyMock.expect(mockLogger.getLogLevel()).andReturn(LogLevel.VERBOSE);
        mockLogger.printLog(LogLevel.VERBOSE, LOG_TAG, testMessage);
        // second thread should inherit same logger
        EasyMock.expect(mockLogger.getLogLevel()).andReturn(LogLevel.ERROR);
        mockLogger.printLog(LogLevel.ERROR, LOG_TAG, testMessage);

        EasyMock.replay(mockLogger);

        ThreadGroup tg = new ThreadGroup("TestThreadGroup");
        Thread firstThread = new Thread(tg, new FirstThread());
        firstThread.start();

        try {
            firstThread.join();  // threaded, but force serialization for testing
        }
        catch (InterruptedException ie) {
            Assert.fail("Thread was unexpectedly interrupted.");
        }
    }

    /** Tests {@link LogRegistry#getLogEntriesAfter}. */
    @Test
    public void testGetLogEntriesAfter() {
        mLogRegistry.printLog(LogLevel.VERBOSE, LOG_TAG, "message1");
        mLogRegistry.printLog(LogLevel.VERBOSE, LOG_TAG, "message2");
        List<LogEntry> logs = mLogRegistry.getLogEntriesAfter(null);
        Assert.assertEquals(2, logs.size());
        Assert.assertEquals("message1", logs.get(0).getMessage());
        Assert.assertEquals("message2", logs.get(1).getMessage());
        mLogRegistry.printLog(LogLevel.VERBOSE, LOG_TAG, "message3");
        mLogRegistry.printLog(LogLevel.VERBOSE, LOG_TAG, "message4");
        logs = mLogRegistry.getLogEntriesAfter(logs.get(1));
        Assert.assertEquals(2, logs.size());
        Assert.assertEquals("message3", logs.get(0).getMessage());
        Assert.assertEquals("message4", logs.get(1).getMessage());
    }

    /** Tests {@link LogRegistry#getLogEntriesAfter} will not store more than limit. */
    @Test
    public void testGetLogEntriesAfter_exceedLimit() {
        for (int i = 0; i < MAX_HISTORY_SIZE * 2; ++i) {
            mLogRegistry.printLog(LogLevel.VERBOSE, LOG_TAG, String.format("message-%d", i));
        }
        List<LogEntry> logs = mLogRegistry.getLogEntriesAfter(null);
        Assert.assertEquals(MAX_HISTORY_SIZE, logs.size());
        Assert.assertEquals("message-9", logs.get(logs.size() - 1).getMessage());
    }
}
