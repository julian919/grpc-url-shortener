package com.example.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.auth.user.RoleEnum;

public interface RoleRepository extends JpaRepository<RoleEnum, String> {
}
