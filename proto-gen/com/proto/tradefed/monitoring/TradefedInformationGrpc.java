package com.proto.tradefed.monitoring;

import static io.grpc.stub.ClientCalls.asyncUnaryCall;
import static io.grpc.stub.ClientCalls.asyncServerStreamingCall;
import static io.grpc.stub.ClientCalls.asyncClientStreamingCall;
import static io.grpc.stub.ClientCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ClientCalls.blockingUnaryCall;
import static io.grpc.stub.ClientCalls.blockingServerStreamingCall;
import static io.grpc.stub.ClientCalls.futureUnaryCall;
import static io.grpc.MethodDescriptor.generateFullMethodName;
import static io.grpc.stub.ServerCalls.asyncUnaryCall;
import static io.grpc.stub.ServerCalls.asyncServerStreamingCall;
import static io.grpc.stub.ServerCalls.asyncClientStreamingCall;
import static io.grpc.stub.ServerCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall;

/**
 * <pre>
 * A service associated with a Tradefed Instance that gives information.
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.0.3)",
    comments = "Source: tradefed_service.proto")
public class TradefedInformationGrpc {

  private TradefedInformationGrpc() {}

  public static final String SERVICE_NAME = "tradefed.monitoring.server.TradefedInformation";

  // Static method descriptors that strictly reflect the proto.
  @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/1901")
  public static final io.grpc.MethodDescriptor<com.proto.tradefed.monitoring.GetInvocationsRequest,
      com.proto.tradefed.monitoring.GetInvocationsResponse> METHOD_GET_INVOCATIONS =
      io.grpc.MethodDescriptor.create(
          io.grpc.MethodDescriptor.MethodType.UNARY,
          generateFullMethodName(
              "tradefed.monitoring.server.TradefedInformation", "getInvocations"),
          io.grpc.protobuf.ProtoUtils.marshaller(com.proto.tradefed.monitoring.GetInvocationsRequest.getDefaultInstance()),
          io.grpc.protobuf.ProtoUtils.marshaller(com.proto.tradefed.monitoring.GetInvocationsResponse.getDefaultInstance()));

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static TradefedInformationStub newStub(io.grpc.Channel channel) {
    return new TradefedInformationStub(channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static TradefedInformationBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    return new TradefedInformationBlockingStub(channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary and streaming output calls on the service
   */
  public static TradefedInformationFutureStub newFutureStub(
      io.grpc.Channel channel) {
    return new TradefedInformationFutureStub(channel);
  }

  /**
   * <pre>
   * A service associated with a Tradefed Instance that gives information.
   * </pre>
   */
  public static abstract class TradefedInformationImplBase implements io.grpc.BindableService {

    /**
     */
    public void getInvocations(com.proto.tradefed.monitoring.GetInvocationsRequest request,
        io.grpc.stub.StreamObserver<com.proto.tradefed.monitoring.GetInvocationsResponse> responseObserver) {
      asyncUnimplementedUnaryCall(METHOD_GET_INVOCATIONS, responseObserver);
    }

    @java.lang.Override public io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            METHOD_GET_INVOCATIONS,
            asyncUnaryCall(
              new MethodHandlers<
                com.proto.tradefed.monitoring.GetInvocationsRequest,
                com.proto.tradefed.monitoring.GetInvocationsResponse>(
                  this, METHODID_GET_INVOCATIONS)))
          .build();
    }
  }

  /**
   * <pre>
   * A service associated with a Tradefed Instance that gives information.
   * </pre>
   */
  public static final class TradefedInformationStub extends io.grpc.stub.AbstractStub<TradefedInformationStub> {
    private TradefedInformationStub(io.grpc.Channel channel) {
      super(channel);
    }

    private TradefedInformationStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected TradefedInformationStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new TradefedInformationStub(channel, callOptions);
    }

    /**
     */
    public void getInvocations(com.proto.tradefed.monitoring.GetInvocationsRequest request,
        io.grpc.stub.StreamObserver<com.proto.tradefed.monitoring.GetInvocationsResponse> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(METHOD_GET_INVOCATIONS, getCallOptions()), request, responseObserver);
    }
  }

  /**
   * <pre>
   * A service associated with a Tradefed Instance that gives information.
   * </pre>
   */
  public static final class TradefedInformationBlockingStub extends io.grpc.stub.AbstractStub<TradefedInformationBlockingStub> {
    private TradefedInformationBlockingStub(io.grpc.Channel channel) {
      super(channel);
    }

    private TradefedInformationBlockingStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected TradefedInformationBlockingStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new TradefedInformationBlockingStub(channel, callOptions);
    }

    /**
     */
    public com.proto.tradefed.monitoring.GetInvocationsResponse getInvocations(com.proto.tradefed.monitoring.GetInvocationsRequest request) {
      return blockingUnaryCall(
          getChannel(), METHOD_GET_INVOCATIONS, getCallOptions(), request);
    }
  }

  /**
   * <pre>
   * A service associated with a Tradefed Instance that gives information.
   * </pre>
   */
  public static final class TradefedInformationFutureStub extends io.grpc.stub.AbstractStub<TradefedInformationFutureStub> {
    private TradefedInformationFutureStub(io.grpc.Channel channel) {
      super(channel);
    }

    private TradefedInformationFutureStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected TradefedInformationFutureStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new TradefedInformationFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.proto.tradefed.monitoring.GetInvocationsResponse> getInvocations(
        com.proto.tradefed.monitoring.GetInvocationsRequest request) {
      return futureUnaryCall(
          getChannel().newCall(METHOD_GET_INVOCATIONS, getCallOptions()), request);
    }
  }

  private static final int METHODID_GET_INVOCATIONS = 0;

  private static class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final TradefedInformationImplBase serviceImpl;
    private final int methodId;

    public MethodHandlers(TradefedInformationImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_GET_INVOCATIONS:
          serviceImpl.getInvocations((com.proto.tradefed.monitoring.GetInvocationsRequest) request,
              (io.grpc.stub.StreamObserver<com.proto.tradefed.monitoring.GetInvocationsResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    return new io.grpc.ServiceDescriptor(SERVICE_NAME,
        METHOD_GET_INVOCATIONS);
  }

}
