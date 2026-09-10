package com.example.auth.security;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.stereotype.Component;

@Component
@GlobalServerInterceptor
public class BasicAuthServerInterceptor implements ServerInterceptor {

  public static final Context.Key<ClientCredentials> CLIENT_CREDENTIALS_KEY =
      Context.key("client_credentials");

  private static final Metadata.Key<String> AUTHORIZATION_KEY =
      Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

  public record ClientCredentials(String clientId, String clientSecret) {}

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

    String authHeader = headers.get(AUTHORIZATION_KEY);
    if (authHeader != null && authHeader.regionMatches(true, 0, "Basic ", 0, 6)) {
      String base64Credentials = authHeader.substring(6).trim();
      try {
        byte[] decoded = Base64.getDecoder().decode(base64Credentials);
        String credentials = new String(decoded, StandardCharsets.UTF_8);
        int colon = credentials.indexOf(':');
        if (colon != -1) {
          String clientId = credentials.substring(0, colon);
          String clientSecret = credentials.substring(colon + 1);
          Context context = Context.current().withValue(
              CLIENT_CREDENTIALS_KEY, new ClientCredentials(clientId, clientSecret));
          return Contexts.interceptCall(context, call, headers, next);
        }
      } catch (IllegalArgumentException ignored) {
        // Fall through if malformed base64
      }
    }

    return next.startCall(call, headers);
  }
}
