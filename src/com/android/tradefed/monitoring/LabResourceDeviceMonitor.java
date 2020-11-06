/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tradefed.monitoring;

import com.android.loganalysis.util.config.OptionClass;
import com.android.tradefed.cluster.ClusterHostUtil;
import com.android.tradefed.cluster.IClusterOptions;
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.device.DeviceAllocationState;
import com.android.tradefed.device.IDeviceMonitor;
import com.android.tradefed.log.LogUtil;

import com.google.dualhomelab.monitoringagent.resourcemonitoring.Attribute;
import com.google.dualhomelab.monitoringagent.resourcemonitoring.LabResource;
import com.google.dualhomelab.monitoringagent.resourcemonitoring.LabResourceRequest;
import com.google.dualhomelab.monitoringagent.resourcemonitoring.LabResourceServiceGrpc;
import com.google.dualhomelab.monitoringagent.resourcemonitoring.Metric;
import com.google.dualhomelab.monitoringagent.resourcemonitoring.MonitoredEntity;
import com.google.dualhomelab.monitoringagent.resourcemonitoring.Resource;
import com.google.protobuf.util.Timestamps;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;

/** The cached LabResource response data. */
class CachedLabResource {
    final LabResource mLabResource;
    final Instant mInstant;

    CachedLabResource(LabResource labResource) {
        this.mLabResource = labResource;
        this.mInstant = Instant.now();
    }

    /** Checks if the cached data is valid or not. */
    boolean isValid(long lifeTimeSec) {
        return Instant.now().minusSeconds(lifeTimeSec).isBefore(mInstant);
    }
}

/** The lab resource monitor which initializes/manages the gRPC server for LabResourceService. */
@OptionClass(alias = "lab-resource-monitor")
public class LabResourceDeviceMonitor extends LabResourceServiceGrpc.LabResourceServiceImplBase
        implements IDeviceMonitor {
    public static final String SERVER_HOSTNAME = "localhost";
    public static final String DEVICE_SERIAL_KEY = "device_serial";
    public static final String HOST_NAME_KEY = "hostname";
    public static final String LAB_NAME_KEY = "lab_name";
    public static final String TEST_HARNESS_KEY = "test_harness";
    public static final String TEST_HARNESS = "tradefed";
    public static final String HOST_GROUP_KEY = "host_group";
    public static final int DEFAULT_PORT = 8887;
    public static final int DEFAULT_THREAD_COUNT = 1;
    public static final long DEFAULT_MAX_CACHE_AGE_SEC = 60;
    public static final String POOL_ATTRIBUTE_NAME = "pool";
    public static final String RUN_TARGET_ATTRIBUTE_NAME = "run_target";
    public static final String STATUS_RESOURCE_NAME = "status";
    public static final float FIXED_METRIC_VALUE = 1.0f;
    private Optional<Server> mServer = Optional.empty();
    private Optional<CachedLabResource> mCachedLabResource = Optional.empty();
    private final long mMaxCacheAgeSec;
    private final IClusterOptions mClusterOptions;
    private DeviceLister mDeviceLister;

    LabResourceDeviceMonitor() {
        super();
        mMaxCacheAgeSec = DEFAULT_MAX_CACHE_AGE_SEC;
        mClusterOptions = ClusterHostUtil.getClusterOptions();
    }

    LabResourceDeviceMonitor(long maxCacheAgeSec, IClusterOptions clusterOptions) {
        super();
        mMaxCacheAgeSec = maxCacheAgeSec;
        mClusterOptions = clusterOptions;
    }

    Optional<Server> getServer() {
        return mServer;
    }

    Optional<CachedLabResource> getCachedLabResource() {
        return mCachedLabResource;
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        if (!mServer.isPresent()) {
            mServer =
                    Optional.of(
                            NettyServerBuilder.forAddress(
                                            new InetSocketAddress(SERVER_HOSTNAME, DEFAULT_PORT))
                                    .addService(this)
                                    .executor(Executors.newFixedThreadPool(DEFAULT_THREAD_COUNT))
                                    .build());
            try {
                mServer.get().start();
            } catch (IOException e) {
                LogUtil.CLog.e(e);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void stop() {
        mServer.ifPresent(Server::shutdown);
    }

    /** {@inheritDoc} */
    @Override
    public void setDeviceLister(DeviceLister lister) {
        mDeviceLister = lister;
    }

    /** {@inheritDoc} */
    @Override
    public void notifyDeviceStateChange(
            String serial, DeviceAllocationState oldState, DeviceAllocationState newState) {
        // Ignore
    }

    /** The gRPC request handler. */
    @Override
    public void getLabResource(
            LabResourceRequest request, StreamObserver<LabResource> responseObserver) {
        LabResource response;
        if (mCachedLabResource.isPresent() && mCachedLabResource.get().isValid(mMaxCacheAgeSec)) {
            response = mCachedLabResource.get().mLabResource;
        } else {
            response =
                    LabResource.newBuilder()
                            .setHost(buildMonitoredHost())
                            .addAllDevice(
                                    mDeviceLister
                                            .listDevices()
                                            .stream()
                                            .filter(d -> !d.isTemporary())
                                            .map(this::buildMonitoredDevice)
                                            .collect(Collectors.toList()))
                            .build();
            mCachedLabResource = Optional.of(new CachedLabResource(response));
        }
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /** Fetches host identifier, attributes and metrics. */
    MonitoredEntity buildMonitoredHost() {
        IClusterOptions options = mClusterOptions;
        return MonitoredEntity.newBuilder()
                .putIdentifier(HOST_NAME_KEY, ClusterHostUtil.getHostName())
                .putIdentifier(LAB_NAME_KEY, options.getLabName())
                .putIdentifier(TEST_HARNESS_KEY, TEST_HARNESS)
                .addAttribute(
                        Attribute.newBuilder()
                                .setName(HOST_GROUP_KEY)
                                .setValue(options.getClusterId()))
                .build();
    }

    /** Fetches device identifier, attributes and metrics. */
    MonitoredEntity buildMonitoredDevice(DeviceDescriptor descriptor) {
        MonitoredEntity.Builder device = MonitoredEntity.newBuilder();
        device.putIdentifier(DEVICE_SERIAL_KEY, descriptor.getSerial())
                .addAllAttribute(getDeviceAttributes(descriptor))
                .addResource(getStatus(descriptor));
        return device.build();
    }

    /** Gets device attributes. */
    List<Attribute> getDeviceAttributes(DeviceDescriptor descriptor) {
        final List<Attribute> attributes = new ArrayList<>();
        attributes.add(
                Attribute.newBuilder()
                        .setName(RUN_TARGET_ATTRIBUTE_NAME)
                        .setValue(
                                ClusterHostUtil.getRunTarget(
                                        descriptor,
                                        mClusterOptions.getRunTargetFormat(),
                                        mClusterOptions.getDeviceTag()))
                        .build());
        attributes.addAll(
                mClusterOptions
                        .getNextClusterIds()
                        .stream()
                        .map(
                                pool ->
                                        Attribute.newBuilder()
                                                .setName(POOL_ATTRIBUTE_NAME)
                                                .setValue(pool)
                                                .build())
                        .collect(Collectors.toList()));
        return attributes;
    }

    /** Gets device status. */
    Resource getStatus(DeviceDescriptor descriptor) {
        return Resource.newBuilder()
                .setResourceName(STATUS_RESOURCE_NAME)
                .setTimestamp(Timestamps.fromMillis(Instant.now().toEpochMilli()))
                .addMetric(
                        Metric.newBuilder()
                                .setTag(descriptor.getState().name())
                                .setValue(FIXED_METRIC_VALUE))
                .build();
    }
}
