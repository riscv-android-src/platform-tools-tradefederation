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
package com.android.tradefed.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tradefed.util.RunInterruptedException;
import com.android.tradefed.util.RunUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link CommandInterrupter} */
@RunWith(JUnit4.class)
public class CommandInterrupterTest {

    private static final String MESSAGE = "message";

    private CommandInterrupter mInterrupter;

    @Before
    public void setUp() {
        mInterrupter = new CommandInterrupter();
    }

    @Test
    public void testAllowInterrupt() throws InterruptedException {
        execute(
                () -> {
                    // interrupts initially blocked
                    assertFalse(mInterrupter.isInterruptAllowed());

                    // thread can be made interruptible
                    mInterrupter.allowInterrupt(true);
                    assertTrue(mInterrupter.isInterruptAllowed());
                });
    }

    @Test
    public void testInterrupt() throws InterruptedException {
        execute(
                () -> {
                    try {
                        // can interrupt the thread
                        mInterrupter.allowInterrupt(true);
                        mInterrupter.interrupt(Thread.currentThread(), MESSAGE);
                        fail("RunInterruptedException was expected");
                    } catch (RunInterruptedException e) {
                        assertEquals(MESSAGE, e.getMessage());
                    }
                });
    }

    @Test
    public void testInterrupt_blocked() throws InterruptedException {
        execute(
                () -> {
                    // track whether interrupts were successfully blocked
                    boolean success = false;

                    try {
                        // not interrupted if interrupts disallowed
                        mInterrupter.allowInterrupt(false);
                        mInterrupter.interrupt(Thread.currentThread(), MESSAGE);
                        success = true;

                        // interrupted once interrupts allowed
                        mInterrupter.allowInterrupt(true);
                        fail("RunInterruptedException was expected");
                    } catch (RunInterruptedException e) {
                        assertEquals(MESSAGE, e.getMessage());
                        assertTrue(success);
                    }
                });
    }

    @Test
    public void testSetInterruptibleInFuture() throws InterruptedException {
        execute(
                () -> {
                    try {
                        // allow interruptions after a delay
                        mInterrupter.setInterruptibleInFuture(Thread.currentThread(), 200L);

                        // not yet marked as interruptible
                        RunUtil.getDefault().sleep(50);
                        assertFalse(mInterrupter.isInterruptAllowed());

                        // marked as interruptible after enough time has passed
                        RunUtil.getDefault().sleep(200L);
                        assertTrue(mInterrupter.isInterruptAllowed());
                    } finally {
                        mInterrupter.terminateTimer();
                    }
                });
    }

    @Test
    public void testSetInterruptibleInFuture_alreadyAllowed() throws InterruptedException {
        execute(
                () -> {
                    try {
                        // interrupts allowed
                        mInterrupter.allowInterrupt(true);

                        // unchanged after asynchronously allowing interrupts
                        mInterrupter.setInterruptibleInFuture(Thread.currentThread(), 200L);
                        assertTrue(mInterrupter.isInterruptAllowed());
                    } finally {
                        mInterrupter.terminateTimer();
                    }
                });
    }

    // Execute test in separate thread
    private static void execute(Runnable runnable) throws InterruptedException {
        Thread thread = new Thread(runnable, "CommandInterrupterTest");
        thread.setDaemon(true);
        thread.start();
        thread.join();
    }
}
