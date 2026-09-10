package com.example.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * RS256 key material for both MINTING and (now that CreatePrincipal is permission-gated)
 * VALIDATING tokens. No PEM needs copying for the decoder side -- auth-service already holds
 * both halves of its own key for minting, so the decoder just reuses the same {@link RSAKey}
 * bean, unlike shortener-service, which validates against a copied public-key file.
 *
 * <p>PEM parsing is done by hand with plain {@link KeyFactory} rather than relying on Spring
 * to convert a {@code classpath:...} string directly to {@link RSAPublicKey}/{@link
 * RSAPrivateKey} via {@code @Value} -- that generic conversion (unlike the well-known
 * {@code spring.security.oauth2.resourceserver.jwt.public-key-location} property, which Boot
 * parses internally) isn't registered for arbitrary custom properties.
 */
@Configuration
public class JwtKeyConfig {

  @Bean
  RSAKey rsaKey(
      @Value("${app.jwt.public-key}") Resource publicKeyResource,
      @Value("${app.jwt.private-key}") Resource privateKeyResource)
      throws Exception {
    RSAPublicKey publicKey = readPublicKey(publicKeyResource);
    RSAPrivateKey privateKey = readPrivateKey(privateKeyResource);
    return new RSAKey.Builder(publicKey)
        .privateKey(privateKey)
        .keyID("auth-service-key-1") // static, so it's stable across restarts
        .build();
  }

  @Bean
  JWKSource<SecurityContext> jwkSource(RSAKey rsaKey) {
    return new ImmutableJWKSet<>(new JWKSet(rsaKey));
  }

  @Bean
  JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
    return new NimbusJwtEncoder(jwkSource);
  }

  @Bean
  JwtDecoder jwtDecoder(RSAKey rsaKey) throws Exception {
    return NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  private static RSAPublicKey readPublicKey(Resource resource) throws Exception {
    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    return (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(decodePem(resource)));
  }

  private static RSAPrivateKey readPrivateKey(Resource resource) throws Exception {
    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    return (RSAPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(decodePem(resource)));
  }

  private static byte[] decodePem(Resource resource) throws IOException {
    String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    String base64Body = content.replaceAll("-----(BEGIN|END)[^-]+-----", "").replaceAll("\\s", "");
    return Base64.getDecoder().decode(base64Body);
  }
}
