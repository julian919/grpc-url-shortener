package com.example.shortener.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.grpc.server.security.AuthenticationProcessInterceptor;
import org.springframework.grpc.server.security.GrpcSecurity;
import org.springframework.security.config.Customizer;

/**
 * Authentication uses Spring gRPC's own {@code GrpcSecurity.oauth2ResourceServer(jwt)} --
 * verified this session to correctly re-hydrate SecurityContextHolder on every callback
 * (onMessage/onHalfClose/onReady), safe against grpc-java's thread-hopping. Authorization
 * reads the required permission off the proto contract itself via
 * {@link ProtoPermissionAuthorizationManager}, not a Java string literal here.
 */
@Configuration
public class GrpcSecurityConfig {

  @Bean
  @GlobalServerInterceptor
  AuthenticationProcessInterceptor shortenerAuthorizationInterceptor(GrpcSecurity grpc) throws Exception {
    grpc.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
        .authorizeRequests(requests -> requests.allRequests().access(new ProtoPermissionAuthorizationManager()));
    return grpc.build();
  }
}
