package com.example.commons.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.grpc.server.security.AuthenticationProcessInterceptor;
import org.springframework.grpc.server.security.GrpcSecurity;
import org.springframework.security.config.Customizer;

/**
 * Turns a gRPC service into a resource server: every inbound call has its JWT verified, and each
 * RPC's required permission is read off the proto contract itself.
 *
 * <p>Contributed by depending on this module. Registered through
 * META-INF/spring/...AutoConfiguration.imports, because this package sits outside the consuming
 * application's component-scan root.
 *
 * <p>Three pieces, and they form a chain:
 *
 * <ol>
 *   <li>this class -- the WIRING. One interceptor in front of every call.
 *   <li>{@link ProtoPermissionAuthorizationManager} -- the POLICY it consults.
 *   <li>{@link AccessTokenExceptionHandler} -- the ERROR MAPPER for when either says no.
 * </ol>
 *
 * <p>Each bean is {@code @ConditionalOnMissingBean}, so a service that needs something different
 * can still declare its own and win.
 */
@AutoConfiguration
@ConditionalOnClass(GrpcSecurity.class)
public class GrpcSecurityAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public ProtoPermissionAuthorizationManager protoPermissionAuthorizationManager() {
    return new ProtoPermissionAuthorizationManager();
  }

  @Bean
  @GlobalServerInterceptor
  @ConditionalOnMissingBean(AuthenticationProcessInterceptor.class)
  public AuthenticationProcessInterceptor grpcAuthorizationInterceptor(
      GrpcSecurity grpc, ProtoPermissionAuthorizationManager authorizationManager) throws Exception {
    grpc.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
        .authorizeRequests(requests -> requests.allRequests().access(authorizationManager));
    return grpc.build();
  }

  /**
   * HIGHEST_PRECEDENCE so it is consulted before a service's own {@code @GrpcAdvice}: an
   * authentication failure must map to ACCESS_TOKEN_* rather than falling through to a generic
   * handler.
   */
  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  @ConditionalOnMissingBean
  public AccessTokenExceptionHandler accessTokenExceptionHandler() {
    return new AccessTokenExceptionHandler();
  }
}
