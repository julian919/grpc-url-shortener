package com.example.shortener.link;

import com.example.shortener.link.exception.AuthorNotActiveException;
import com.example.user.api.GetUserRequest;
import com.example.user.api.User;
import com.example.user.api.UserServiceGrpc;
import com.example.user.api.UserStatus;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.stereotype.Component;

/** gRPC-backed {@link AuthorDirectory}: one GetUser call against user-service. */
@Component
public class GrpcAuthorDirectory implements AuthorDirectory {

  private final UserServiceGrpc.UserServiceBlockingStub userServiceStub;

  public GrpcAuthorDirectory(UserServiceGrpc.UserServiceBlockingStub userServiceStub) {
    this.userServiceStub = userServiceStub;
  }

  @Override
  public void requireActiveAuthor(String principalId) {
    User user;
    try {
      user =
          userServiceStub
              .getUser(GetUserRequest.newBuilder().setPrincipalId(principalId).build())
              .getUser();
    } catch (StatusRuntimeException e) {
      // A principal with a valid token but no profile row is not a link author -- treat it as a
      // refusal, not an outage. Anything else (user-service down, deadline) propagates as-is so
      // the caller sees UNAVAILABLE rather than a misleading "not active".
      if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
        throw new AuthorNotActiveException("no user profile for principal " + principalId);
      }
      throw e;
    }

    if (user.getStatus() != UserStatus.USER_STATUS_ACTIVE) {
      throw new AuthorNotActiveException("author is " + user.getStatus().name());
    }
  }
}
