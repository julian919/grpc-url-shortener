package com.example.user.config;

import com.example.auth.api.AuthServiceGrpc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

/** Mirrors shortener-service's own GrpcClientConfig -- channels.createChannel("auth")
 * resolves against spring.grpc.client.channel.auth.* in application.yml. */
@Configuration
public class GrpcClientConfig {

  @Bean
  AuthServiceGrpc.AuthServiceBlockingStub authServiceStub(GrpcChannelFactory channels) {
    return AuthServiceGrpc.newBlockingStub(channels.createChannel("auth"));
  }
}
