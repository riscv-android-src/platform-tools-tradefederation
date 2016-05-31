/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.UniqueMultiMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generic implementation of a {@link IInvocationMetadata}.
 */
public class InvocationMetadata implements IInvocationMetadata {

    private Map<ITestDevice, IBuildInfo> mAllocatedDeviceAndBuildMap;
    private Map<String, ITestDevice> mNameAndDeviceMap;
    private Map<String, IBuildInfo> mNameAndBuildinfoMap;
    private final UniqueMultiMap<String, String> mInvocationAttributes =
            new UniqueMultiMap<String, String>();

    /**
     * Creates a {@link BuildInfo} using default attribute values.
     */
    public InvocationMetadata() {
        mAllocatedDeviceAndBuildMap = new HashMap<ITestDevice, IBuildInfo>();
        mNameAndDeviceMap = new HashMap<String, ITestDevice>();
        mNameAndBuildinfoMap = new HashMap<String, IBuildInfo>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNumDevicesAllocated() {
        return mAllocatedDeviceAndBuildMap.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAllocatedDevice(String devicename, ITestDevice testDevices) {
        mNameAndDeviceMap.put(devicename, testDevices);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAllocatedDevice(Map<String, ITestDevice> deviceWithName) {
        mNameAndDeviceMap.putAll(deviceWithName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<ITestDevice, IBuildInfo> getDeviceBuildMap() {
        return mAllocatedDeviceAndBuildMap;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ITestDevice> getDevices() {
        return new ArrayList<ITestDevice>(mNameAndDeviceMap.values());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getSerials() {
        List<String> listSerials = new ArrayList<String>();
        for (ITestDevice testDevice : mNameAndDeviceMap.values()) {
            listSerials.add(testDevice.getSerialNumber());
        }
        return listSerials;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getDeviceConfigNames() {
        List<String> listNames = new ArrayList<String>();
        listNames.addAll(mNameAndDeviceMap.keySet());
        return listNames;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice(String deviceName) {
        return mNameAndDeviceMap.get(deviceName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IBuildInfo getBuildInfo(String deviceName) {
        return mNameAndBuildinfoMap.get(deviceName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addDeviceBuildInfo(String deviceName, IBuildInfo buildinfo) {
        mNameAndBuildinfoMap.put(deviceName, buildinfo);
        mAllocatedDeviceAndBuildMap.put(getDevice(deviceName), buildinfo);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IBuildInfo getBuildInfo(ITestDevice testDevice) {
        return mAllocatedDeviceAndBuildMap.get(testDevice);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addInvocationAttribute(String attributeName, String attributeValue) {
        mInvocationAttributes.put(attributeName, attributeValue);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MultiMap<String, String> getAttributes() {
        return mInvocationAttributes;
    }
}
