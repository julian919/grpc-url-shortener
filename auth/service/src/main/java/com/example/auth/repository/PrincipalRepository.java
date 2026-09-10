package com.example.auth.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.auth.user.PrincipalEntity;

public interface PrincipalRepository extends JpaRepository<PrincipalEntity, UUID> {
}
