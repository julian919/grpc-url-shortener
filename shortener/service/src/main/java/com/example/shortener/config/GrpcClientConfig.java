package com.example.shortener.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

import com.example.linkaudit.api.LinkAuditServiceGrpc;

/**
 * A gRPC channel is expensive and long-lived: it owns the HTTP/2 connections
 * and, later, the
 * load-balancing policy. It is created once here and shared, never per request.
 *
 * <p>
 * {@code channels.createChannel("link-audit")} resolves against the
 * {@code spring.grpc.client.channel.link-audit.*} properties in
 * application.yml.
 */
@Configuration
public class GrpcClientConfig {

  @Bean
  LinkAuditServiceGrpc.LinkAuditServiceBlockingStub linkAuditStub(GrpcChannelFactory channels) {
    return LinkAuditServiceGrpc.newBlockingStub(channels.createChannel("link-audit"));
  }
}
