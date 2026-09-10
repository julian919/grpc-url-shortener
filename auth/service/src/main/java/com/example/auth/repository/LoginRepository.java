package com.example.auth.repository;

import com.example.auth.user.AuthProviderEnum;
import com.example.auth.user.LoginEntity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginRepository extends JpaRepository<LoginEntity, UUID> {

  Optional<LoginEntity> findByProviderAndAccountId(AuthProviderEnum provider, String accountId);
}
