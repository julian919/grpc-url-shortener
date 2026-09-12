package com.example.user.config;

import com.example.auth.api.AuthServiceGrpc;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.interceptor.security.BearerTokenAuthenticationInterceptor;
import org.springframework.grpc.client.interceptor.security.TokenSupplier;

/**
 * The {@code plain} stub and the {@link TokenSupplier} come from auth-client's auto-configuration.
 * All this service adds is the AUTHENTICATED stub: the plain one decorated with a bearer-token
 * interceptor, used for every call that is not "fetch me a token".
 */
@Configuration
public class GrpcClientConfig {

  @Bean
  @Qualifier("authenticated")
  AuthServiceGrpc.AuthServiceBlockingStub authenticatedAuthServiceStub(
      @Qualifier("plain") AuthServiceGrpc.AuthServiceBlockingStub plainStub,
      TokenSupplier serviceTokenSupplier) {
    return plainStub.withInterceptors(new BearerTokenAuthenticationInterceptor(serviceTokenSupplier));
  }
}
