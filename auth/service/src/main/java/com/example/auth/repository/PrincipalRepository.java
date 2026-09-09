package com.example.auth.repository;

import com.example.auth.entity.Principal;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrincipalRepository extends JpaRepository<Principal, UUID> {
}
