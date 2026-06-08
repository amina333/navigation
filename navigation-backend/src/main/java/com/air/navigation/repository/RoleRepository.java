package com.air.navigation.repository;

import com.air.navigation.enums.ERole;
import com.air.navigation.entities.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Integer> {

    Optional<Role> findByName(ERole name);

    boolean existsByName(ERole name);
}
