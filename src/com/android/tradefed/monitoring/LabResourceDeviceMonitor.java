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
import com.android.tradefed.device.DeviceAllocationState;
import com.android.tradefed.device.IDeviceMonitor;
import com.android.tradefed.log.LogUtil;

import com.google.common.annotations.VisibleForTesting;
import com.google.dualhomelab.monitoringagent.resourcemonitoring.LabResource;
import com.google.dualhomelab.monitoringagent.resourcemonitoring.LabResourceRequest;
import com.google.dualhomelab.monitoringagent.resourcemonitoring.LabResourceServiceGrpc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.Executors;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;

/** The lab resource monitor which initializes/manages the gRPC server for LabResourceService. */
@OptionClass(alias = "lab-resource-monitor")
public class LabResourceDeviceMonitor extends LabResourceServiceGrpc.LabResourceServiceImplBase
        implements IDeviceMonitor {
    public static final String SERVER_HOSTNAME = "localhost";
    public static final int DEFAULT_PORT = 8887;
    public static final int DEFAULT_THREAD_COUNT = 1;
    private Optional<Server> mServer = Optional.empty();

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
        // Ignore
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
        super.getLabResource(request, responseObserver);
    }
}
