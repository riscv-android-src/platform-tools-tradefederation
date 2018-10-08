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

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.RunInterruptedException;

import com.google.common.annotations.VisibleForTesting;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/** Service allowing TradeFederation commands to be interrupted or marked as uninterruptible. */
public class CommandInterrupter {

    /** Singleton. */
    public static final CommandInterrupter INSTANCE = new CommandInterrupter();

    private Map<Thread, Boolean> mMapIsInterruptAllowed = new HashMap<>();
    private Map<Thread, String> mMapInterruptThreads = new HashMap<>();
    private Map<Thread, Timer> mWatchdogInterrupt = new HashMap<>();

    @VisibleForTesting
    // FIXME: reduce visibility once RunUtil interrupt tests are removed
    public CommandInterrupter() {}

    /** Remove the thread that are not alive anymore from our tracking to keep the list small. */
    private void cleanInterruptStateThreadMap() {
        synchronized (mMapIsInterruptAllowed) {
            for (Iterator<Thread> iterator = mMapIsInterruptAllowed.keySet().iterator();
                    iterator.hasNext();
                    ) {
                Thread t = iterator.next();
                if (!t.isAlive()) {
                    iterator.remove();
                }
            }
        }
    }

    /**
     * Allows/disallows run interrupts on the current thread. If it is allowed, run operations of
     * the current thread can be interrupted from other threads via {@link #interrupt} method.
     *
     * @param allow whether to allow run interrupts on the current thread.
     */
    public void allowInterrupt(boolean allow) {
        CLog.d("run interrupt allowed: %s", allow);
        synchronized (mMapIsInterruptAllowed) {
            mMapIsInterruptAllowed.put(Thread.currentThread(), allow);
        }
        checkInterrupted();
    }

    /**
     * Give the interrupt status of the RunUtil.
     *
     * @return true if the Run can be interrupted, false otherwise.
     */
    public boolean isInterruptAllowed() {
        synchronized (mMapIsInterruptAllowed) {
            if (mMapIsInterruptAllowed.get(Thread.currentThread()) == null) {
                // We don't add in this case to keep the map relatively small.
                return false;
            }
            return mMapIsInterruptAllowed.get(Thread.currentThread());
        }
    }

    /**
     * Set as interruptible after some waiting time. {@link CommandScheduler#shutdownHard()} to
     * enforce we terminate eventually.
     *
     * @param thread the thread that will become interruptible.
     * @param timeMs time to wait before setting interruptible.
     */
    // FIXME: reduce visibility once RunUtil interrupt methods are removed
    public void setInterruptibleInFuture(Thread thread, final long timeMs) {
        CLog.w("Setting future interruption in %s ms", timeMs);
        synchronized (mMapIsInterruptAllowed) {
            if (Boolean.TRUE.equals(mMapIsInterruptAllowed.get(thread))) {
                CLog.v("Thread is already interruptible. setInterruptibleInFuture is inop.");
                return;
            }
        }
        Timer timer = new Timer(true);
        synchronized (mWatchdogInterrupt) {
            mWatchdogInterrupt.put(thread, timer);
        }
        timer.schedule(new InterruptTask(thread), timeMs);
    }

    /**
     * Interrupts the ongoing/forthcoming run operations on the given thread. The run operations on
     * the given thread will throw {@link RunInterruptedException}.
     *
     * @param thread
     * @param message the message for {@link RunInterruptedException}.
     */
    // FIXME: reduce visibility once RunUtil interrupt methods are removed
    public synchronized void interrupt(Thread thread, String message) {
        if (message == null) {
            throw new IllegalArgumentException("message cannot be null.");
        }
        mMapInterruptThreads.put(thread, message);
        checkInterrupted();
    }

    public synchronized void checkInterrupted() {
        // Keep the map of thread's state clean of dead threads.
        this.cleanInterruptStateThreadMap();

        final Thread thread = Thread.currentThread();
        if (isInterruptAllowed()) {
            final String message = mMapInterruptThreads.remove(thread);
            if (message != null) {
                thread.interrupt();
                throw new RunInterruptedException(message);
            }
        }
    }

    /** Allow to stop the Timer Thread for the run util instance if started. */
    @VisibleForTesting
    // FIXME: reduce visibility once RunUtil interrupt tests are removed
    public void terminateTimer() {
        if (mWatchdogInterrupt != null && !mWatchdogInterrupt.isEmpty()) {
            for (Timer t : mWatchdogInterrupt.values()) {
                t.purge();
                t.cancel();
            }
        }
    }

    /** Timer that will execute a interrupt on the Thread registered. */
    private class InterruptTask extends TimerTask {

        private Thread mToInterrupt = null;

        public InterruptTask(Thread t) {
            mToInterrupt = t;
        }

        @Override
        public void run() {
            if (mToInterrupt != null) {
                synchronized (mWatchdogInterrupt) {
                    // Ensure that the timer associated with the task is cancelled too.
                    mWatchdogInterrupt.get(mToInterrupt).cancel();
                }

                CLog.e("Interrupting with TimerTask");
                synchronized (mMapIsInterruptAllowed) {
                    mMapIsInterruptAllowed.put(mToInterrupt, true);
                }
                mToInterrupt.interrupt();

                synchronized (mWatchdogInterrupt) {
                    mWatchdogInterrupt.remove(mToInterrupt);
                }
            }
        }
    }
}
