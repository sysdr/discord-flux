package com.flux.grpc;

import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.flux.grpc.proto.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.time.Instant;

public class MessageServiceImpl extends MessageServiceGrpc.MessageServiceImplBase {
    
    @Override
    public void insertMessage(InsertMessageRequest request, 
                            StreamObserver<InsertMessageResponse> responseObserver) {
        MetricsCollector.recordRequest("InsertMessage");
        try {
            ScyllaDBClient.getSession(); // Attempt connection if not connected
            if (!ScyllaDBClient.isConnected()) {
                responseObserver.onError(Status.UNAVAILABLE
                    .withDescription("Database not connected")
                    .asRuntimeException());
                return;
            }
            
            BoundStatement bound = ScyllaDBClient.getInsertStatement().bind(
                request.getChannelId(),
                request.getMessageId(),
                request.getAuthorId(),
                request.getContent(),
                Instant.now().toEpochMilli()
            );
            
            ScyllaDBClient.getSession().execute(bound);
            
            responseObserver.onNext(InsertMessageResponse.newBuilder()
                .setSuccess(true)
                .build());
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            responseObserver.onNext(InsertMessageResponse.newBuilder()
                .setSuccess(false)
                .setError(e.getMessage())
                .build());
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void getMessage(GetMessageRequest request, 
                          StreamObserver<Message> responseObserver) {
        try {
            ScyllaDBClient.getSession(); // Attempt connection if not connected
            if (!ScyllaDBClient.isConnected()) {
                responseObserver.onError(Status.UNAVAILABLE
                    .withDescription("Database not connected")
                    .asRuntimeException());
                return;
            }
            
            BoundStatement bound = ScyllaDBClient.getSelectStatement().bind(
                request.getChannelId(),
                request.getMessageId()
            );
            
            ResultSet rs = ScyllaDBClient.getSession().execute(bound);
            Row row = rs.one();
            
            if (row == null) {
                responseObserver.onError(Status.NOT_FOUND
                    .withDescription("Message not found")
                    .asRuntimeException());
                return;
            }
            
            MetricsCollector.recordRequest("GetMessage");
            
            Message message = Message.newBuilder()
                .setChannelId(row.getLong("channel_id"))
                .setMessageId(row.getLong("message_id"))
                .setAuthorId(row.getLong("author_id"))
                .setContent(row.getString("content"))
                .setTimestamp(row.getLong("timestamp"))
                .build();
            
            responseObserver.onNext(message);
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL
                .withDescription(e.getMessage())
                .asRuntimeException());
        }
    }
    
    @Override
    public void streamMessageHistory(StreamHistoryRequest request, 
                                     StreamObserver<Message> responseObserver) {
        try {
            ScyllaDBClient.getSession(); // Attempt connection if not connected
            if (!ScyllaDBClient.isConnected()) {
                responseObserver.onError(Status.UNAVAILABLE
                    .withDescription("Database not connected")
                    .asRuntimeException());
                return;
            }
            
            int limit = request.getLimit() > 0 ? request.getLimit() : 100;
            
            BoundStatement bound = ScyllaDBClient.getSelectHistoryStatement().bind(
                request.getChannelId(),
                limit
            );
            
            ResultSet rs = ScyllaDBClient.getSession().execute(bound);
            
            int count = 0;
            for (Row row : rs) {
                Message message = Message.newBuilder()
                    .setChannelId(row.getLong("channel_id"))
                    .setMessageId(row.getLong("message_id"))
                    .setAuthorId(row.getLong("author_id"))
                    .setContent(row.getString("content"))
                    .setTimestamp(row.getLong("timestamp"))
                    .build();
                
                responseObserver.onNext(message);
                count++;
            }
            
            MetricsCollector.recordRequest("StreamMessageHistory");
            MetricsCollector.recordStreamedMessages(count);
            
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL
                .withDescription(e.getMessage())
                .asRuntimeException());
        }
    }
    
    @Override
    public void deleteMessage(DeleteMessageRequest request, 
                             StreamObserver<DeleteMessageResponse> responseObserver) {
        try {
            ScyllaDBClient.getSession(); // Attempt connection if not connected
            if (!ScyllaDBClient.isConnected()) {
                responseObserver.onError(Status.UNAVAILABLE
                    .withDescription("Database not connected")
                    .asRuntimeException());
                return;
            }
            
            BoundStatement bound = ScyllaDBClient.getDeleteStatement().bind(
                request.getChannelId(),
                request.getMessageId()
            );
            
            ScyllaDBClient.getSession().execute(bound);
            
            MetricsCollector.recordRequest("DeleteMessage");
            
            responseObserver.onNext(DeleteMessageResponse.newBuilder()
                .setSuccess(true)
                .build());
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL
                .withDescription(e.getMessage())
                .asRuntimeException());
        }
    }
}
