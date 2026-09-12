package com.example.commons.security;

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
 * This project's own trimmed version of Cognixus's real
 * {@code AuthorizationUtil.validateRequiredRolesAndPermissions} -- reads the required
 * permission straight off the RPC's own proto {@code MethodOptions}, via the same handle
 * ({@code MethodDescriptor.getSchemaDescriptor()}) Cognixus's version uses, rather than as a
 * Java string literal in a config class. See lessons/... for the full story of why.
 *
 * <p>Deliberately does not special-case "no authentication at all" versus "authenticated but
 * missing the permission" -- both just return a denied decision, the same way
 * {@code .hasAuthority(...)} itself would. The UNAUTHENTICATED vs PERMISSION_DENIED split is
 * the framework's own job (the same translation {@code .hasAuthority()} relies on), not this
 * class's.
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
