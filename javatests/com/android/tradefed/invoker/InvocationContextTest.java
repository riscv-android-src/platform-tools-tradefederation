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
package com.android.tradefed.invoker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.ConfigurationDescriptor;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.proto.InvocationContext.Context;
import com.android.tradefed.testtype.suite.ModuleDefinition;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.SerializationUtil;
import com.android.tradefed.util.UniqueMultiMap;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.util.Arrays;

/** Unit tests for {@link InvocationContext} */
@RunWith(JUnit4.class)
public class InvocationContextTest {

    private InvocationContext mContext;

    @Before
    public void setUp() {
        mContext = new InvocationContext();
    }

    /** Test setting and getting invocation ID. */
    @Test
    public void testGetInvocationID() {
        // initially null
        assertNull(mContext.getInvocationId());

        // non-null after adding the ID as an attribute
        mContext.addInvocationAttribute(IInvocationContext.INVOCATION_ID, "TEST_ID");
        assertEquals("TEST_ID", mContext.getInvocationId());
    }

    /** Test the reverse look up of the device name in the configuration for an ITestDevice */
    @Test
    public void testGetDeviceName() {
        ITestDevice device1 = mock(ITestDevice.class);
        ITestDevice device2 = mock(ITestDevice.class);
        // assert that at init, map is empty.
        assertNull(mContext.getDeviceName(device1));
        mContext.addAllocatedDevice("test1", device1);
        assertEquals("test1", mContext.getDeviceName(device1));
        assertNull(mContext.getDeviceName(device2));
    }

    /** Test the reverse look up of the device name in the configuration for an IBuildInfo */
    @Test
    public void testGetBuildInfoName() {
        IBuildInfo build1 = mock(IBuildInfo.class);
        IBuildInfo build2 = mock(IBuildInfo.class);
        // assert that at init, map is empty.
        assertNull(mContext.getBuildInfoName(build1));
        mContext.addDeviceBuildInfo("test1", build1);
        assertEquals("test1", mContext.getBuildInfoName(build1));
        assertNull(mContext.getBuildInfoName(build2));
    }

    /**
     * Test adding attributes and querying them. The map returned is always a copy and does not
     * affect the actual invocation attributes.
     */
    @Test
    public void testGetAttributes() {
        mContext.addInvocationAttribute("TEST_KEY", "TEST_VALUE");
        assertEquals(Arrays.asList("TEST_VALUE"), mContext.getAttributes().get("TEST_KEY"));
        MultiMap<String, String> map = mContext.getAttributes();
        map.remove("TEST_KEY");
        // assert that the key is still there in the map from the context
        assertEquals(Arrays.asList("TEST_VALUE"), mContext.getAttributes().get("TEST_KEY"));
    }

    /** Test that once locked the invocation context does not accept more invocation attributes. */
    @Test
    public void testLockedContext() {
        mContext.lockAttributes();
        try {
            mContext.addInvocationAttribute("test", "Test");
            fail("Should have thrown an exception.");
        } catch (IllegalStateException expected) {
            // expected
        }
        try {
            mContext.addInvocationAttributes(new UniqueMultiMap<>());
            fail("Should have thrown an exception.");
        } catch (IllegalStateException expected) {
            // expected
        }
    }

    /** Test that serializing and deserializing an {@link InvocationContext}. */
    @Test
    public void testSerialize() throws Exception {
        assertNotNull(mContext.getDeviceBuildMap());
        ITestDevice device = mock(ITestDevice.class);
        IBuildInfo info = new BuildInfo("1234", "test-target");
        mContext.addAllocatedDevice("test-device", device);
        mContext.addDeviceBuildInfo("test-device", info);
        mContext.setConfigurationDescriptor(new ConfigurationDescriptor());
        assertEquals(info, mContext.getBuildInfo(device));
        File ser = SerializationUtil.serialize(mContext);
        try {
            InvocationContext deserialized =
                    (InvocationContext) SerializationUtil.deserialize(ser, true);
            // One consequence is that transient attribute will become null but our custom
            // deserialization should fix that.
            assertNotNull(deserialized.getDeviceBuildMap());
            assertNotNull(deserialized.getConfigurationDescriptor());
            assertEquals(info, deserialized.getBuildInfo("test-device"));

            // The device are not carried
            assertTrue(deserialized.getDevices().isEmpty());
            // Re-assigning a device, recreate the previous relationships
            deserialized.addAllocatedDevice("test-device", device);
            assertEquals(info, mContext.getBuildInfo(device));
        } finally {
            FileUtil.deleteFile(ser);
        }
    }

    @Test
    public void testProtoSerialize() {
        InvocationContext context = new InvocationContext();
        ConfigurationDescriptor descriptor = new ConfigurationDescriptor();
        descriptor.setModuleName("module");
        context.setConfigurationDescriptor(descriptor);
        context.setTestTag("tag");
        context.addInvocationAttribute("test_key", "test_value");
        Context protoContext = context.toProto();
        // Check the proto
        assertEquals("tag", protoContext.getTestTag());
        assertEquals(1, protoContext.getMetadataList().size());
        assertEquals("test_key", protoContext.getMetadataList().get(0).getKey());

        // Check the deserialize
        InvocationContext deserialized = InvocationContext.fromProto(protoContext);
        assertNull(deserialized.getModuleInvocationContext());
        assertNotNull(deserialized.getConfigurationDescriptor());
        assertEquals("tag", deserialized.getTestTag());
    }

    @Test
    public void testProtoSerialize_moduleContext() {
        InvocationContext context = new InvocationContext();
        ConfigurationDescriptor descriptor = new ConfigurationDescriptor();
        descriptor.setModuleName("module");
        context.setConfigurationDescriptor(descriptor);

        InvocationContext moduleContext = new InvocationContext();
        moduleContext.addInvocationAttribute(ModuleDefinition.MODULE_ID, "module-id");
        moduleContext.setConfigurationDescriptor(new ConfigurationDescriptor());
        context.setModuleInvocationContext(moduleContext);

        Context protoContext = context.toProto();
        assertNotNull(protoContext.getModuleContext());

        // Check the deserialize
        InvocationContext deserialized = InvocationContext.fromProto(protoContext);
        assertNotNull(deserialized.getModuleInvocationContext());
        assertEquals(
                "module-id",
                deserialized
                        .getModuleInvocationContext()
                        .getAttributes()
                        .get(ModuleDefinition.MODULE_ID)
                        .get(0));
    }
}
