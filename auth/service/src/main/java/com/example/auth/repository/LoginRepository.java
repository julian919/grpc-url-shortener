package com.example.auth.repository;

import com.example.auth.entity.AuthProvider;
import com.example.auth.entity.Login;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginRepository extends JpaRepository<Login, UUID> {

  Optional<Login> findByProviderAndAccountId(AuthProvider provider, String accountId);
}
