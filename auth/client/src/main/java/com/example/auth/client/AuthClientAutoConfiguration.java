package com.example.auth.client;

import com.example.auth.api.AuthServiceGrpc;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.grpc.client.interceptor.security.TokenSupplier;

/**
 * Everything a service needs to authenticate itself to auth-service, contributed by adding this
 * module as a dependency. Registered through META-INF/spring/...AutoConfiguration.imports, so it
 * is picked up even though it sits outside the consuming application's component-scan package.
 *
 * <p>What a consumer gets:
 *
 * <ul>
 *   <li>a {@code plain} stub -- unintercepted, used ONLY to fetch a token (going through the
 *       authenticated stub would be circular: a token is needed to get a token)
 *   <li>a {@link TokenSupplier} that fetches and caches the client-credentials token
 * </ul>
 *
 * <p>Consumers decorate their OWN outbound stubs with
 * {@code BearerTokenAuthenticationInterceptor(tokenSupplier)} -- shortener-service attaches it to
 * its user-service stub, not to an auth-service one.
 */
@AutoConfiguration
@ConditionalOnClass(GrpcChannelFactory.class)
@EnableConfigurationProperties(AuthClientProperties.class)
public class AuthClientAutoConfiguration {

  /** Resolves against spring.grpc.client.channel.auth.* in the consuming service's config. */
  @Bean
  @Qualifier("plain")
  public AuthServiceGrpc.AuthServiceBlockingStub plainAuthServiceStub(GrpcChannelFactory channels) {
    return AuthServiceGrpc.newBlockingStub(channels.createChannel("auth"));
  }

  @Bean
  public TokenSupplier serviceTokenSupplier(
      @Qualifier("plain") AuthServiceGrpc.AuthServiceBlockingStub plainAuthServiceStub,
      AuthClientProperties properties) {
    return new ServiceTokenSupplier(
        plainAuthServiceStub, properties.clientId(), properties.clientSecret());
  }
}
