package com.example.auth.config;

import com.example.auth.api.AuthProto;
import com.example.auth.api.RequiresPermissions;
import io.grpc.MethodDescriptor;
import io.grpc.protobuf.ProtoMethodDescriptorSupplier;
import java.util.function.Supplier;
import org.springframework.grpc.server.security.CallContext;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;

/**
 * Identical logic to shortener-service's class of the same name -- a second copy, not shared,
 * matching how GrpcClientConfig is already duplicated per-service rather than pulled into a
 * library. Reads the required permission straight off the RPC's own proto MethodOptions.
 */
public class ProtoPermissionAuthorizationManager implements AuthorizationManager<Object> {

  @Override
  public AuthorizationDecision authorize(
      Supplier<? extends Authentication> authenticationSupplier, Object object) {
    CallContext context = (CallContext) object;
    RequiresPermissions required = requiredPermissions(context.method());

    if (required.getPermissionsCount() == 0) {
      return new AuthorizationDecision(true); // nothing declared on this RPC -- public
    }

    Authentication authentication = authenticationSupplier.get();
    if (authentication == null) {
      return new AuthorizationDecision(false);
    }

    boolean granted =
        required.getPermissionsList().stream()
            .anyMatch(
                permission ->
                    authentication.getAuthorities().stream()
                        .anyMatch(authority -> authority.getAuthority().equals(permission)));
    return new AuthorizationDecision(granted);
  }

  private static RequiresPermissions requiredPermissions(MethodDescriptor<?, ?> methodDescriptor) {
    return ((ProtoMethodDescriptorSupplier) methodDescriptor.getSchemaDescriptor())
        .getMethodDescriptor()
        .getOptions()
        .getExtension(AuthProto.requiresPermissions);
  }
}
