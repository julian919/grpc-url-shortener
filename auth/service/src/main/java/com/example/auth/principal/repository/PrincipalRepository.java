package com.example.auth.principal.repository;

import com.example.auth.principal.entity.PrincipalEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrincipalRepository extends JpaRepository<PrincipalEntity, UUID> {
}
