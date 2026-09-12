package com.example.shortener.config;

import com.example.user.api.UserServiceGrpc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.grpc.client.interceptor.security.BearerTokenAuthenticationInterceptor;
import org.springframework.grpc.client.interceptor.security.TokenSupplier;

/**
 * The outbound call to user-service, carrying shortener's OWN service token -- not the caller's
 * user token. shortener is asking on its own behalf ("is this principal still active?"), which is
 * why it holds the SHORTENER_SERVICE role and its GET_USER permission.
 *
 * <p>The {@link TokenSupplier} comes from auth-client's auto-configuration; all this adds is the
 * stub it decorates.
 */
@Configuration
public class GrpcClientConfig {

  @Bean
  UserServiceGrpc.UserServiceBlockingStub userServiceStub(
      GrpcChannelFactory channels, TokenSupplier serviceTokenSupplier) {
    return UserServiceGrpc.newBlockingStub(channels.createChannel("user"))
        .withInterceptors(new BearerTokenAuthenticationInterceptor(serviceTokenSupplier));
  }
}
