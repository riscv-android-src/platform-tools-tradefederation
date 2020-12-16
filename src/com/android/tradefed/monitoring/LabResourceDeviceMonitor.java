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


import com.android.tradefed.cluster.ClusterHostUtil;
import com.android.tradefed.cluster.IClusterOptions;
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceAllocationState;
import com.android.tradefed.device.IDeviceMonitor;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.monitoring.collector.IResourceMetricCollector;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.MoreExecutors;
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
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;

/**
 * The lab resource monitor which initializes/manages the gRPC server for LabResourceService. To add
 * resource metric collectors, please add resource_metric_collector tags in global configuration to
 * load the collectors.
 */
@OptionClass(alias = "lab-resource-monitor")
public class LabResourceDeviceMonitor extends LabResourceServiceGrpc.LabResourceServiceImplBase
        implements IDeviceMonitor {
    public static final String DEVICE_SERIAL_KEY = "device_serial";
    public static final String HOST_NAME_KEY = "hostname";
    public static final String LAB_NAME_KEY = "lab_name";
    public static final String TEST_HARNESS_KEY = "test_harness";
    public static final String TEST_HARNESS = "tradefed";
    public static final String HOST_GROUP_KEY = "host_group";
    public static final String SERVER_HOSTNAME = "localhost";
    public static final int DEFAULT_PORT = 8887;
    public static final int DEFAULT_THREAD_COUNT = 1;
    public static final String POOL_ATTRIBUTE_NAME = "pool";
    public static final String RUN_TARGET_ATTRIBUTE_NAME = "run_target";
    public static final String STATUS_RESOURCE_NAME = "status";
    public static final float FIXED_METRIC_VALUE = 1.0f;
    private Optional<Server> mServer = Optional.empty();
    private IClusterOptions mClusterOptions;
    private DeviceLister mDeviceLister;
    private final Collection<IResourceMetricCollector> mMetricCollectors = new ArrayList<>();
    private final ReadWriteLock mLabResourceLock = new ReentrantReadWriteLock();
    private LabResource mLabResource = LabResource.newBuilder().build();
    private ScheduledExecutorService mMetricizeExecutor;
    private ExecutorService mSharedCollectorExecutor;

    @Option(
            name = "metricize-op-timeout",
            description =
                    "The maximum wait time in milliseconds for every resource metric collector to"
                            + " get metrics.")
    private long mMetricizeTimeoutMs = 1000;

    @Option(
            name = "metricize-cycle-sec",
            description = "The time in seconds between for each metricize cycle.")
    private long mMetricizeCycleSec = 300;

    public LabResourceDeviceMonitor() {
        super();
    }

    @VisibleForTesting
    LabResourceDeviceMonitor(long metricizeCycleSec, IClusterOptions clusterOptions) {
        super();
        mMetricizeCycleSec = metricizeCycleSec;
        mClusterOptions = clusterOptions;
    }

    private IClusterOptions getClusterOptions() {
        return mClusterOptions == null ? ClusterHostUtil.getClusterOptions() : mClusterOptions;
    }

    private void loadMetricCollectors() {
        List<IResourceMetricCollector> collectors =
                GlobalConfiguration.getInstance().getResourceMetricCollectors();
        if (collectors != null) {
            mMetricCollectors.addAll(collectors);
        }
    }

    @VisibleForTesting
    Optional<Server> getServer() {
        return mServer;
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
                loadMetricCollectors();
                mMetricizeExecutor =
                        MoreExecutors.getExitingScheduledExecutorService(
                                new ScheduledThreadPoolExecutor(1));
                mSharedCollectorExecutor =
                        MoreExecutors.getExitingExecutorService(
                                (ThreadPoolExecutor) Executors.newFixedThreadPool(1));
                scheduleMetricizeTask();
            } catch (IOException e) {
                CLog.e(e);
            }
        }
    }

    private void setCachedLabResource(LabResource resource) {
        mLabResourceLock.writeLock().lock();
        try {
            mLabResource = resource;
        } finally {
            mLabResourceLock.writeLock().unlock();
        }
    }

    private LabResource getCachedLabResource() {
        mLabResourceLock.readLock().lock();
        try {
            return mLabResource;
        } finally {
            mLabResourceLock.readLock().unlock();
        }
    }

    @VisibleForTesting
    ExecutorService getSharedCollectorExecutor() {
        return mSharedCollectorExecutor;
    }

    private void scheduleMetricizeTask() {
        if (mMetricizeExecutor == null || mSharedCollectorExecutor == null) {
            CLog.d(
                    "schedule metricize task before the mMetricizeExecutor or"
                            + " mSharedCollectorExecutor initialized.");
            return;
        }
        mMetricizeExecutor.scheduleAtFixedRate(
                () -> {
                    LabResource.Builder builder =
                            LabResource.newBuilder().setHost(buildMonitoredHost(mMetricCollectors));
                    List<DeviceDescriptor> descriptors =
                            mDeviceLister
                                    .listDevices()
                                    .stream()
                                    .filter(descriptor -> !descriptor.isTemporary())
                                    .collect(Collectors.toList());
                    for (DeviceDescriptor descriptor : descriptors) {
                        if (mMetricizeExecutor.isShutdown()) {
                            break;
                        }
                        builder.addDevice(buildMonitoredDevice(descriptor, mMetricCollectors));
                    }
                    setCachedLabResource(builder.build());
                },
                0,
                mMetricizeCycleSec,
                TimeUnit.SECONDS);
    }

    /** {@inheritDoc} */
    @Override
    public void stop() {
        mServer.ifPresent(Server::shutdownNow);
        mMetricizeExecutor.shutdownNow();
        mSharedCollectorExecutor.shutdownNow();
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
        // ignored
    }

    /** The gRPC request handler. */
    @Override
    public void getLabResource(
            LabResourceRequest request, StreamObserver<LabResource> responseObserver) {
        responseObserver.onNext(getCachedLabResource());
        responseObserver.onCompleted();
    }

    private Collection<Resource> enqueueCollectorTask(Callable<Collection<Resource>> task) {
        Future<Collection<Resource>> future = mSharedCollectorExecutor.submit(task);
        try {
            return future.get(mMetricizeTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            CLog.e(e);
            future.cancel(true);
        }
        return List.of();
    }

    /** Build host {@link MonitoredEntity}. */
    @VisibleForTesting
    MonitoredEntity buildMonitoredHost(Collection<IResourceMetricCollector> collectors) {
        MonitoredEntity.Builder builder =
                MonitoredEntity.newBuilder()
                        .putIdentifier(HOST_NAME_KEY, ClusterHostUtil.getHostName())
                        .putIdentifier(LAB_NAME_KEY, getClusterOptions().getLabName())
                        .putIdentifier(TEST_HARNESS_KEY, TEST_HARNESS)
                        .addAttribute(
                                Attribute.newBuilder()
                                        .setName(HOST_GROUP_KEY)
                                        .setValue(getClusterOptions().getClusterId()));
        for (IResourceMetricCollector collector : collectors) {
            builder.addAllResource(enqueueCollectorTask(collector::getHostResourceMetrics));
        }
        return builder.build();
    }

    /** Builds device {@link MonitoredEntity}. */
    @VisibleForTesting
    MonitoredEntity buildMonitoredDevice(
            DeviceDescriptor descriptor, Collection<IResourceMetricCollector> collectors) {
        MonitoredEntity.Builder builder = MonitoredEntity.newBuilder();
        builder.putIdentifier(DEVICE_SERIAL_KEY, descriptor.getSerial());
        builder.addAllAttribute(getDeviceAttributes(descriptor));
        builder.addResource(getStatus(descriptor));
        for (IResourceMetricCollector collector : collectors) {
            builder.addAllResource(
                    enqueueCollectorTask(
                            () ->
                                    collector.getDeviceResourceMetrics(
                                            descriptor,
                                            GlobalConfiguration.getDeviceManagerInstance())));
        }
        return builder.build();
    }

    /** Gets device attributes. */
    @VisibleForTesting
    List<Attribute> getDeviceAttributes(DeviceDescriptor descriptor) {
        final List<Attribute> attributes = new ArrayList<>();
        attributes.add(
                Attribute.newBuilder()
                        .setName(RUN_TARGET_ATTRIBUTE_NAME)
                        .setValue(
                                ClusterHostUtil.getRunTarget(
                                        descriptor,
                                        getClusterOptions().getRunTargetFormat(),
                                        getClusterOptions().getDeviceTag()))
                        .build());
        attributes.addAll(
                getClusterOptions().getNextClusterIds().stream()
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
    @VisibleForTesting
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
