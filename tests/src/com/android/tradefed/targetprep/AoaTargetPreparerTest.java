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
package com.android.tradefed.targetprep;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.android.helper.aoa.AoaDevice;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.awt.*;
import java.time.Duration;

/** Unit tests for {@link AoaTargetPreparer} */
@RunWith(JUnit4.class)
public class AoaTargetPreparerTest {

    private AoaTargetPreparer mPreparer;

    private AoaDevice mDevice;

    @Before
    public void setUp() {
        mDevice = mock(AoaDevice.class);
        mPreparer = new AoaTargetPreparer();
    }

    @Test
    public void testClick() {
        mPreparer.execute(mDevice, "click 1 23");

        verify(mDevice, times(1)).click(eq(new Point(1, 23)));
        verifyNoMoreInteractions(mDevice);
    }

    @Test
    public void testLongClick() {
        mPreparer.execute(mDevice, "longClick 23 4");

        verify(mDevice, times(1)).longClick(eq(new Point(23, 4)));
        verifyNoMoreInteractions(mDevice);
    }

    @Test
    public void testScroll() {
        mPreparer.execute(mDevice, "scroll 3 45 6 78");

        verify(mDevice, times(1)).scroll(eq(new Point(3, 45)), eq(new Point(6, 78)));
        verifyNoMoreInteractions(mDevice);
    }

    @Test
    public void testFling() {
        mPreparer.execute(mDevice, "fling 45 6 78 9");

        verify(mDevice, times(1)).fling(eq(new Point(45, 6)), eq(new Point(78, 9)));
        verifyNoMoreInteractions(mDevice);
    }

    @Test
    public void testDrag() {
        mPreparer.execute(mDevice, "drag 5 67 8 90");

        verify(mDevice, times(1)).drag(eq(new Point(5, 67)), eq(new Point(8, 90)));
        verifyNoMoreInteractions(mDevice);
    }

    @Test
    public void testWrite() {
        mPreparer.execute(mDevice, "write lorem ipsum");

        verify(mDevice, times(1)).write(eq("lorem ipsum"));
        verifyNoMoreInteractions(mDevice);
    }

    @Test
    public void testKey() {
        mPreparer.execute(mDevice, "key 44");
        // accepts hexadecimal values
        mPreparer.execute(mDevice, "key 0x2C");

        verify(mDevice, times(2)).key(eq(44));
        verifyNoMoreInteractions(mDevice);
    }

    @Test
    public void testKey_multiple() {
        mPreparer.execute(mDevice, "key 1 2 3 4 5");

        verify(mDevice, times(1)).key(eq(1), eq(2), eq(3), eq(4), eq(5));
        verifyNoMoreInteractions(mDevice);
    }

    @Test
    public void testKey_repeated() {
        mPreparer.execute(mDevice, "key 3* 0x2C");

        verify(mDevice, times(1)).key(eq(44), eq(44), eq(44));
        verifyNoMoreInteractions(mDevice);
    }

    @Test
    public void testWake() {
        mPreparer.execute(mDevice, "wake");

        verify(mDevice, times(1)).wakeUp();
        verifyNoMoreInteractions(mDevice);
    }

    @Test
    public void testHome() {
        mPreparer.execute(mDevice, "home");

        verify(mDevice, times(1)).goHome();
        verifyNoMoreInteractions(mDevice);
    }

    @Test
    public void testBack() {
        mPreparer.execute(mDevice, "back");

        verify(mDevice, times(1)).goBack();
        verifyNoMoreInteractions(mDevice);
    }

    @Test
    public void testSleep() {
        mPreparer.execute(mDevice, "sleep PT10M");

        verify(mDevice, times(1)).sleep(eq(Duration.ofMinutes(10L)));
        verifyNoMoreInteractions(mDevice);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalid_unknownKeyword() {
        mPreparer.execute(mDevice, "jump 12 3");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalid_missingCoordinates() {
        mPreparer.execute(mDevice, "click");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalid_tooFewCoordinates() {
        mPreparer.execute(mDevice, "longClick 1");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalid_tooManyCoordinates() {
        mPreparer.execute(mDevice, "scroll 1 2 3 4 5");
    }
}
