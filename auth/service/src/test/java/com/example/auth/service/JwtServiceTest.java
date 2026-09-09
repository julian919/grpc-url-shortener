package com.example.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.auth.entity.Principal;
import com.example.auth.entity.Role;
import com.example.auth.repository.RoleRepository;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * A real, freshly-generated RSA keypair and real Nimbus encoder/decoder -- not mocked. What's
 * worth testing here is whether the issued token's claim SHAPE matches what shortener-service's
 * GrpcSecurity config expects (authorities-claim-name: permissions), which no mock can confirm.
 */
@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

  @Mock private RoleRepository roleRepository;

  private JwtDecoder jwtDecoder;
  private JwtService jwtService;

  @BeforeEach
  void setUp() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair keyPair = generator.generateKeyPair();
    RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
    RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

    RSAKey rsaKey = new RSAKey.Builder(publicKey).privateKey(privateKey).keyID("test-key").build();
    JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(rsaKey));
    JwtEncoder jwtEncoder = new NimbusJwtEncoder(jwkSource);
    jwtDecoder = NimbusJwtDecoder.withPublicKey(publicKey).build();

    jwtService = new JwtService(jwtEncoder, roleRepository, "https://auth-service-test", Duration.ofMinutes(15));
  }

  @Test
  void issueAccessToken_expandsRolesIntoTheirCatalogPermissions() {
    Principal principal = new Principal("hash-not-relevant-here", Set.of("USER"));
    when(roleRepository.findAllById(Set.of("USER")))
        .thenReturn(List.of(new Role("USER", Set.of("CREATE_SHORT_URL"))));

    String token = jwtService.issueAccessToken(principal);
    Jwt decoded = jwtDecoder.decode(token);

    assertThat(decoded.getSubject()).isEqualTo(principal.getId().toString());
    assertThat(decoded.getIssuer().toString()).isEqualTo("https://auth-service-test");
    assertThat(decoded.<List<String>>getClaim("roles")).containsExactly("USER");
    // The permission comes from the ROLE's catalog entry, not principal.getPermissions()
    // directly -- this is the "expansion happens once, at mint time" behavior JwtService's
    // own class-level javadoc claims.
    assertThat(decoded.<List<String>>getClaim("permissions")).containsExactly("CREATE_SHORT_URL");
  }

  @Test
  void issueAccessToken_includesDirectGrantsAlongsideRolePermissions() {
    Principal principal = new Principal("hash-not-relevant-here", Set.of());
    principal.getPermissions().add("DIRECT_GRANT");
    when(roleRepository.findAllById(any())).thenReturn(List.of());

    String token = jwtService.issueAccessToken(principal);
    Jwt decoded = jwtDecoder.decode(token);

    assertThat(decoded.<List<String>>getClaim("permissions")).containsExactly("DIRECT_GRANT");
  }
}
