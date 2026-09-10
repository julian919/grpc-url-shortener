package com.example.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.grpc.server.security.AuthenticationProcessInterceptor;
import org.springframework.grpc.server.security.GrpcSecurity;
import org.springframework.security.config.Customizer;

/**
 * A first for this service: previously auth-service only MINTED tokens, never validated an
 * incoming one. Now that CreatePrincipal is permission-gated, it needs the same
 * authentication + proto-declared-permission wiring shortener-service already has.
 * Login/GetClientToken/RenewToken stay public automatically -- ProtoPermissionAuthorizationManager
 * grants when an RPC declares no required permission.
 */
@Configuration
public class GrpcSecurityConfig {

  @Bean
  @GlobalServerInterceptor
  AuthenticationProcessInterceptor authAuthorizationInterceptor(GrpcSecurity grpc) throws Exception {
    grpc.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
        .authorizeRequests(requests -> requests.allRequests().access(new ProtoPermissionAuthorizationManager()));
    return grpc.build();
  }
}
