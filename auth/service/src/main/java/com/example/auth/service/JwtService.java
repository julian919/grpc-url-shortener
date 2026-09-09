package com.example.auth.service;

import com.example.auth.entity.Principal;
import com.example.auth.repository.RoleRepository;
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
 * Mints RS256 access tokens. Permission expansion (role names -> the catalog's permissions)
 * happens once, HERE, at mint time -- every downstream service just reads the token's
 * {@code permissions} claim directly, never re-resolving roles against this service's own DB.
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

  public Duration accessTokenTtl() {
    return accessTokenTtl;
  }

  public String issueAccessToken(Principal principal) {
    Instant now = Instant.now();
    var claims =
        JwtClaimsSet.builder()
            .issuer(issuerUri)
            .subject(principal.getId().toString())
            .claim("roles", List.copyOf(principal.getRoles()))
            .claim("permissions", List.copyOf(effectivePermissions(principal)))
            .issuedAt(now)
            .expiresAt(now.plus(accessTokenTtl))
            .build();
    var header = JwsHeader.with(SignatureAlgorithm.RS256).build();
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  private Set<String> effectivePermissions(Principal principal) {
    Set<String> permissions = new HashSet<>(principal.getPermissions());
    roleRepository.findAllById(principal.getRoles()).forEach(role -> permissions.addAll(role.getPermissions()));
    return permissions;
  }
}
