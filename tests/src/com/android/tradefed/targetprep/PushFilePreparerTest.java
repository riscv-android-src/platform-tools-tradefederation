/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;

import junit.framework.TestCase;

import org.easymock.EasyMock;
import org.mockito.Mockito;

import java.io.File;
import java.util.Arrays;

/**
 * Unit tests for {@link PushFilePreparer}
 */
public class PushFilePreparerTest extends TestCase {

    private PushFilePreparer mPreparer = null;
    private ITestDevice mMockDevice = null;
    private OptionSetter mOptionSetter = null;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockDevice = EasyMock.createStrictMock(ITestDevice.class);
        EasyMock.expect(mMockDevice.getDeviceDescriptor()).andStubReturn(null);
        EasyMock.expect(mMockDevice.getSerialNumber()).andStubReturn("SERIAL");
        mPreparer = new PushFilePreparer();
        mOptionSetter = new OptionSetter(mPreparer);
    }

    /**
     * When there's nothing to be done, expect no exception to be thrown
     */
    public void testNoop() throws Exception {
        EasyMock.replay(mMockDevice);
        mPreparer.setUp(mMockDevice, null);
    }

    public void testLocalNoExist() throws Exception {
        mOptionSetter.setOptionValue("push", "/noexist->/data/");
        mOptionSetter.setOptionValue("post-push", "ls /");
        EasyMock.replay(mMockDevice);
        try {
            // Should throw TargetSetupError and _not_ run any post-push command
            mPreparer.setUp(mMockDevice, null);
            fail("TargetSetupError not thrown");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    public void testRemoteNoExist() throws Exception {
        mOptionSetter.setOptionValue("push", "/bin/sh->/noexist/");
        mOptionSetter.setOptionValue("post-push", "ls /");
        // expect a pushFile() call and return false (failed)
        EasyMock.expect(
                mMockDevice.pushFile((File)EasyMock.anyObject(), EasyMock.eq("/noexist/")))
                .andReturn(Boolean.FALSE);
        EasyMock.replay(mMockDevice);
        try {
            // Should throw TargetSetupError and _not_ run any post-push command
            mPreparer.setUp(mMockDevice, null);
            fail("TargetSetupError not thrown");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    public void testWarnOnFailure() throws Exception {
        mOptionSetter.setOptionValue("push", "/bin/sh->/noexist/");
        mOptionSetter.setOptionValue("push", "/noexist->/data/");
        mOptionSetter.setOptionValue("post-push", "ls /");
        mOptionSetter.setOptionValue("abort-on-push-failure", "false");

        // expect a pushFile() call and return false (failed)
        EasyMock.expect(
                mMockDevice.pushFile((File)EasyMock.anyObject(), EasyMock.eq("/noexist/")))
                .andReturn(Boolean.FALSE);
        // Because we're only warning, the post-push command should be run despite the push failures
        EasyMock.expect(mMockDevice.executeShellCommand(EasyMock.eq("ls /"))).andReturn("");
        EasyMock.replay(mMockDevice);

        // Don't expect any exceptions to be thrown
        mPreparer.setUp(mMockDevice, null);
    }

    public void testPushFromTestCasesDir() throws Exception {
        mOptionSetter.setOptionValue("push", "sh->/noexist/");
        mOptionSetter.setOptionValue("post-push", "ls /");

        PushFilePreparer spyPreparer = Mockito.spy(mPreparer);
        Mockito.doReturn(Arrays.asList(new File("/bin"))).when(spyPreparer).getTestCasesDirs();

        // expect a pushFile() call as /bin/sh should exist and return false (failed)
        EasyMock.expect(mMockDevice.pushFile((File) EasyMock.anyObject(), EasyMock.eq("/noexist/")))
                .andReturn(Boolean.FALSE);
        EasyMock.replay(mMockDevice);
    }
}

