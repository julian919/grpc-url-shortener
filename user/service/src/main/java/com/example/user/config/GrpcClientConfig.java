package com.example.user.config;

import com.example.auth.api.AuthServiceGrpc;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.grpc.client.interceptor.security.BearerTokenAuthenticationInterceptor;
import org.springframework.grpc.client.interceptor.security.TokenSupplier;

/**
 * Two stub beans, deliberately: {@code plain} is used only by {@code ServiceTokenSupplier} to
 * fetch a token in the first place (using the authenticated stub there would be circular -- a
 * token is needed to get a token). {@code authenticated} attaches a bearer token to every
 * other call, via Spring gRPC's real {@link BearerTokenAuthenticationInterceptor}.
 */
@Configuration
public class GrpcClientConfig {

  @Bean
  @Qualifier("plain")
  AuthServiceGrpc.AuthServiceBlockingStub authServiceStub(GrpcChannelFactory channels) {
    return AuthServiceGrpc.newBlockingStub(channels.createChannel("auth"));
  }

  @Bean
  @Qualifier("authenticated")
  AuthServiceGrpc.AuthServiceBlockingStub authenticatedAuthServiceStub(
      @Qualifier("plain") AuthServiceGrpc.AuthServiceBlockingStub authServiceStub,
      TokenSupplier serviceTokenSupplier) {
    return authServiceStub.withInterceptors(new BearerTokenAuthenticationInterceptor(serviceTokenSupplier));
  }
}
