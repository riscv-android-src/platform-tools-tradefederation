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
package com.android.tradefed.util;

import com.android.tradefed.util.IRunUtil.IRunnableResult;
import com.android.tradefed.util.IRunUtil.EnvPriority;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Unit tests for {@link RunUtilTest}
 */
public class RunUtilTest extends TestCase {

    private RunUtil mRunUtil;
    private long mSleepTime = 0;
    private boolean success = false;
    private static final long SHORT_TIMEOUT_MS = 200;
    private static final long LONG_TIMEOUT_MS = 1000;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mRunUtil = new RunUtil();
    }

    /**
     * Test success case for {@link RunUtil#runTimed(long, IRunnableResult, boolean)}.
     */
    public void testRunTimed() throws Exception {
        IRunUtil.IRunnableResult mockRunnable = EasyMock.createStrictMock(
                IRunUtil.IRunnableResult.class);
        EasyMock.expect(mockRunnable.run()).andReturn(Boolean.TRUE);
        EasyMock.replay(mockRunnable);
        assertEquals(CommandStatus.SUCCESS,
                mRunUtil.runTimed(SHORT_TIMEOUT_MS, mockRunnable, true));
    }

    /**
     * Test failure case for {@link RunUtil#runTimed(long, IRunnableResult, boolean)}.
     */
    public void testRunTimed_failed() throws Exception {
        IRunUtil.IRunnableResult mockRunnable = EasyMock.createStrictMock(
                IRunUtil.IRunnableResult.class);
        EasyMock.expect(mockRunnable.run()).andReturn(Boolean.FALSE);
        EasyMock.replay(mockRunnable);
        assertEquals(CommandStatus.FAILED,
                mRunUtil.runTimed(SHORT_TIMEOUT_MS, mockRunnable, true));
    }

    /**
     * Test exception case for {@link RunUtil#runTimed(long, IRunnableResult, boolean)}.
     */
    public void testRunTimed_exception() throws Exception {
        IRunUtil.IRunnableResult mockRunnable = EasyMock.createStrictMock(
                IRunUtil.IRunnableResult.class);
        EasyMock.expect(mockRunnable.run()).andThrow(new RuntimeException());
        mockRunnable.cancel();
        EasyMock.replay(mockRunnable);
        assertEquals(CommandStatus.EXCEPTION,
                mRunUtil.runTimed(SHORT_TIMEOUT_MS, mockRunnable, true));
    }

    /**
     * Test that {@link RunUtil#runTimedCmd(long, String[])} fails when given a garbage command.
     */
    public void testRunTimedCmd_failed() {
        CommandResult result = mRunUtil.runTimedCmd(1000, "blahggggwarggg");
        assertEquals(CommandStatus.EXCEPTION, result.getStatus());
        assertNull(result.getStdout());
        assertNull(result.getStderr());
    }

    /**
     * Test that {@link RunUtil#runTimedCmd(long, String[])} fails when garbage times out.
     */
    public void testRunTimedCmd_timeout() {
        String[] command = {"sleep", "10000"};
        CommandResult result = mRunUtil.runTimedCmd(SHORT_TIMEOUT_MS, command);
        assertEquals(CommandStatus.TIMED_OUT, result.getStatus());
        assertEquals("", result.getStdout());
        assertEquals("", result.getStderr());
    }

    /**
     * Verify that calling {@link RunUtil#setWorkingDir(File)} is not allowed on default instance.
     */
    public void testSetWorkingDir_default() {
        try {
            RunUtil.getDefault().setWorkingDir(new File("foo"));
            fail("could set working dir on RunUtil.getDefault()");
        } catch (RuntimeException e) {
            // expected
        }
    }

    /**
     * Verify that calling {@link RunUtil#setEnvVariable(String, String)} is not allowed on default
     * instance.
     */
    public void testSetEnvVariable_default() {
        try {
            RunUtil.getDefault().setEnvVariable("foo", "bar");
            fail("could set env var on RunUtil.getDefault()");
        } catch (RuntimeException e) {
            // expected
        }
    }

    /**
     * Verify that calling {@link RunUtil#unsetEnvVariable(String)} is not allowed on default
     * instance.
     */
    public void testUnsetEnvVariable_default() {
        try {
            RunUtil.getDefault().unsetEnvVariable("foo");
            fail("could unset env var on RunUtil.getDefault()");
        } catch (Exception e) {
            // expected
        }
    }

    /**
     * Test that {@link RunUtil#runEscalatingTimedRetry(long, long, long, long, IRunnableResult)}
     * fails when operation continually fails, and that the maxTime variable is respected.
     */
    public void testRunEscalatingTimedRetry_timeout() throws Exception {
        // create a RunUtil fixture with methods mocked out for
        // fast execution

        RunUtil runUtil = new RunUtil() {
            @Override
            public void sleep(long time) {
                mSleepTime += time;
            }

            @Override
            long getCurrentTime() {
                return mSleepTime;
            }

            @Override
            public CommandStatus runTimed(long timeout, IRunUtil.IRunnableResult runnable,
                    boolean logErrors) {
                try {
                    // override parent with simple version that doesn't create a thread
                    return runnable.run() ? CommandStatus.SUCCESS : CommandStatus.FAILED;
                } catch (Exception e) {
                    return CommandStatus.EXCEPTION;
                }
            }
        };

        IRunUtil.IRunnableResult mockRunnable = EasyMock.createStrictMock(
                IRunUtil.IRunnableResult.class);
        // expect a call 4 times, at sleep time 0, 1, 4 and 10 ms
        EasyMock.expect(mockRunnable.run()).andReturn(Boolean.FALSE).times(4);
        EasyMock.replay(mockRunnable);
        long maxTime = 10;
        assertFalse(runUtil.runEscalatingTimedRetry(1, 1, 512, maxTime, mockRunnable));
        assertEquals(maxTime, mSleepTime);
        EasyMock.verify(mockRunnable);
    }

    /**
     * Test a success case for {@link RunUtil#interrupt}.
     */
    public void testInterrupt() {
        final String message = "it is alright now";
        mRunUtil.allowInterrupt(true);
        mRunUtil.interrupt(Thread.currentThread(), message);
        try{
            mRunUtil.sleep(1);
            fail("RunInterruptedException was expected, but not thrown.");
        } catch (final RunInterruptedException e) {
            assertEquals(message, e.getMessage());
        }
    }

    /**
     * Test whether a {@link RunUtil#interrupt} call is respected when called while interrupts are
     * not allowed.
     */
    public void testInterrupt_delayed() {
        final String message = "it is alright now";
        mRunUtil.allowInterrupt(false);
        mRunUtil.interrupt(Thread.currentThread(), message);
        mRunUtil.sleep(1);
        try{
            mRunUtil.allowInterrupt(true);
            mRunUtil.sleep(1);
            fail("RunInterruptedException was expected, but not thrown.");
        } catch (final RunInterruptedException e) {
            assertEquals(message, e.getMessage());
        }
    }

    /**
     * Test whether a {@link RunUtil#interrupt} call is respected when called multiple times.
     */
    public void testInterrupt_multiple() {
        final String message1 = "it is alright now";
        final String message2 = "without a fight";
        final String message3 = "rock this town";
        mRunUtil.allowInterrupt(true);
        mRunUtil.interrupt(Thread.currentThread(), message1);
        mRunUtil.interrupt(Thread.currentThread(), message2);
        mRunUtil.interrupt(Thread.currentThread(), message3);
        try{
            mRunUtil.sleep(1);
            fail("RunInterruptedException was expected, but not thrown.");
        } catch (final RunInterruptedException e) {
            assertEquals(message3, e.getMessage());
        }
    }

    /**
     * Test whether a {@link RunUtil#runTimedCmd(long, OutputStream, OutputStream, String[])}
     * call correctly redirect the output to files.
     */
    public void testRuntimedCmd_withFileOutputStream() {
        File stdout = null;
        File stderr = null;
        OutputStream stdoutStream = null;
        OutputStream stderrStream = null;
        try {
            stdout = FileUtil.createTempFile("stdout_subprocess_", ".txt");
            stdoutStream = new FileOutputStream(stdout);
            stderr = FileUtil.createTempFile("stderr_subprocess_", ".txt");
            stderrStream = new FileOutputStream(stderr);
        } catch (IOException e) {
            fail("Failed to create output files: " + e.getMessage());
        }
        String[] command = {"echo", "TEST"};
        CommandResult result = mRunUtil.runTimedCmd(SHORT_TIMEOUT_MS, stdoutStream, stderrStream,
                command);
        assertEquals(CommandStatus.SUCCESS, result.getStatus());
        assertEquals(result.getStdout(),
                "redirected to " + stdoutStream.getClass().getSimpleName());
        assertEquals(result.getStderr(),
                "redirected to " + stderrStream.getClass().getSimpleName());
        assertTrue(stdout.exists());
        assertTrue(stderr.exists());
        try {
            assertEquals("TEST\n", FileUtil.readStringFromFile(stdout));
            assertEquals("", FileUtil.readStringFromFile(stderr));
        } catch (IOException e) {
            fail(e.getMessage());
        } finally {
            FileUtil.deleteFile(stdout);
            FileUtil.deleteFile(stderr);
        }
    }

    /**
     * Test whether a {@link RunUtil#runTimedCmd(long, OutputStream, OutputStream, String[])}
     * call correctly redirect the output to stdout because files are null.
     */
    public void testRuntimedCmd_regularOutput_fileNull() {
        String[] command = {"echo", "TEST"};
        CommandResult result = mRunUtil.runTimedCmd(SHORT_TIMEOUT_MS, null, null, command);
        assertEquals(CommandStatus.SUCCESS, result.getStatus());
        assertEquals(result.getStdout(), "TEST\n");
        assertEquals(result.getStderr(), "");
    }

    /**
     * Test whether a {@link RunUtil#runTimedCmd(long, OutputStream, OutputStream, String[])}
     * redirect to the file even if they become non-writable afterward.
     */
    public void testRuntimedCmd_notWritable() {
        File stdout = null;
        File stderr = null;
        OutputStream stdoutStream = null;
        OutputStream stderrStream = null;
        try {
            stdout = FileUtil.createTempFile("stdout_subprocess_", ".txt");
            stdoutStream = new FileOutputStream(stdout);
            stdout.setWritable(false);
            stderr = FileUtil.createTempFile("stderr_subprocess_", ".txt");
            stderrStream = new FileOutputStream(stderr);
            stderr.setWritable(false);
        } catch (IOException e) {
            fail("Failed to create output files: " + e.getMessage());
        }
        String[] command = {"echo", "TEST"};
        CommandResult result = mRunUtil.runTimedCmd(SHORT_TIMEOUT_MS, stdoutStream, stderrStream,
                command);
        assertEquals(CommandStatus.SUCCESS, result.getStatus());
        assertEquals(result.getStdout(),
                "redirected to " + stdoutStream.getClass().getSimpleName());
        assertEquals(result.getStderr(),
                "redirected to " + stderrStream.getClass().getSimpleName());
        assertTrue(stdout.exists());
        assertTrue(stderr.exists());
        try {
            assertEquals("TEST\n", FileUtil.readStringFromFile(stdout));
            assertEquals("", FileUtil.readStringFromFile(stderr));
        } catch (IOException e) {
            fail(e.getMessage());
        } finally {
            stdout.setWritable(true);
            stderr.setWritable(true);
            FileUtil.deleteFile(stdout);
            FileUtil.deleteFile(stderr);
        }
    }

    /**
     * Test whether a {@link RunUtil#setInterruptibleInFuture} change properly the interruptible
     * state.
     */
    public void testSetInterruptibleInFuture() {
        final Thread test = new Thread(new Runnable() {
            @Override
            public void run() {
                mRunUtil.allowInterrupt(false);
                assertFalse(mRunUtil.isInterruptAllowed());
                mRunUtil.setInterruptibleInFuture(Thread.currentThread(), 10);
                try {
                    mRunUtil.sleep(25);
                    mRunUtil.sleep(25);
                    fail();
                } catch (RunInterruptedException rie) {
                    assertEquals("TEST", rie.getMessage());
                }
                success = mRunUtil.isInterruptAllowed();
            }
        });
        mRunUtil.interrupt(test, "TEST");
        test.start();
        try {
            test.join();
        } catch (InterruptedException e) {
            // Ignore
        }
        assertTrue(success);
    }

    /**
     * Test whether a {@link RunUtil#setInterruptibleInFuture} has not change the state yet.
     */
    public void testSetInterruptibleInFuture_beforeTimeout() {
        mRunUtil.allowInterrupt(false);
        assertFalse(mRunUtil.isInterruptAllowed());
        mRunUtil.setInterruptibleInFuture(Thread.currentThread(), LONG_TIMEOUT_MS);
        mRunUtil.sleep(10);
        // Should still be false
        assertFalse(mRunUtil.isInterruptAllowed());
    }

    /**
     * Test {@link RunUtil#setEnvVariablePriority(EnvPriority)} properly prioritize unset.
     */
    public void testUnsetPriority() {
        final String ENV_NAME = "TF_GLO";
        RunUtil testRunUtil = new RunUtil();
        testRunUtil.setEnvVariablePriority(EnvPriority.UNSET);
        testRunUtil.setEnvVariable(ENV_NAME, "initvalue");
        testRunUtil.unsetEnvVariable(ENV_NAME);
        CommandResult result =
                testRunUtil.runTimedCmd(LONG_TIMEOUT_MS, "/bin/bash", "-c", "echo $" + ENV_NAME);
        assertNotNull(result.getStdout());
        // Variable should be unset, some echo return empty line break.
        assertEquals("\n", result.getStdout());
    }

    /**
     * Test {@link RunUtil#setEnvVariablePriority(EnvPriority)} properly prioritize set.
     */
    public void testUnsetPriority_inverted() {
        final String ENV_NAME = "TF_GLO";
        final String expected = "initvalue";
        RunUtil testRunUtil = new RunUtil();
        testRunUtil.setEnvVariablePriority(EnvPriority.SET);
        testRunUtil.setEnvVariable(ENV_NAME, expected);
        testRunUtil.unsetEnvVariable(ENV_NAME);
        CommandResult result =
                testRunUtil.runTimedCmd(LONG_TIMEOUT_MS, "/bin/bash", "-c", "echo $" + ENV_NAME);
        assertNotNull(result.getStdout());
        // Variable should be set and returned.
        assertEquals(expected + "\n", result.getStdout());
    }
}
