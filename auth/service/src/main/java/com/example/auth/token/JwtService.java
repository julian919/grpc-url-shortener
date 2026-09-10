package com.example.auth.token;

import com.example.auth.principal.entity.PrincipalEntity;
import com.example.auth.token.entity.RoleEntity;
import com.example.auth.token.repository.RoleRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Mints RS256 access tokens. Permission expansion (role names -> the catalog's
 * permissions)
 * happens once, HERE, at mint time -- every downstream service just reads the
 * token's
 * {@code permissions} claim directly, never re-resolving roles against this
 * service's own DB.
 */
@Service
public class JwtService {

  private final JwtEncoder jwtEncoder;
  private final RoleRepository roleRepository;
  private final String issuerUri;
  private final Duration accessTokenTtl;

  public JwtService(
      JwtEncoder jwtEncoder,
      RoleRepository roleRepository,
      @Value("${app.jwt.issuer-uri}") String issuerUri,
      @Value("${app.jwt.access-token-ttl}") Duration accessTokenTtl) {
    this.jwtEncoder = jwtEncoder;
    this.roleRepository = roleRepository;
    this.issuerUri = issuerUri;
    this.accessTokenTtl = accessTokenTtl;
  }

  // Deliberately short -- a service is expected to re-fetch rather than hold a
  // long-lived credential in memory. No refresh token either, same reasoning.
  private static final Duration SERVICE_TOKEN_TTL = Duration.ofMinutes(5);

  public Duration accessTokenTtl() {
    return accessTokenTtl;
  }

  public Duration serviceTokenTtl() {
    return SERVICE_TOKEN_TTL;
  }

  public String issueAccessToken(PrincipalEntity principal) {
    return issue(principal, accessTokenTtl);
  }

  public String issueServiceToken(PrincipalEntity principal) {
    return issue(principal, SERVICE_TOKEN_TTL);
  }

  private String issue(PrincipalEntity principal, Duration ttl) {
    Instant now = Instant.now();
    var claims = JwtClaimsSet.builder()
        .issuer(issuerUri)
        .subject(principal.getId().toString())
        .claim("roles", List.copyOf(principal.getRoles()))
        .claim("permissions", List.copyOf(effectivePermissions(principal)))
        .issuedAt(now)
        .expiresAt(now.plus(ttl))
        .build();
    var header = JwsHeader.with(SignatureAlgorithm.RS256).build();
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  private Set<String> effectivePermissions(PrincipalEntity principal) {
    Set<String> permissions = new HashSet<>(principal.getPermissions());
    roleRepository.findAllById(principal.getRoles()).forEach(role -> permissions.addAll(role.getPermissions()));
    return permissions;
  }
}
