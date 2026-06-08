package com.air.navigation.config;

import com.air.navigation.entities.Role;
import com.air.navigation.entities.User;
import com.air.navigation.enums.ERole;
import com.air.navigation.repository.RoleRepository;
import com.air.navigation.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository    userRepository;
    private final RoleRepository    roleRepository;
    private final PasswordEncoder   passwordEncoder;

    // ── Configurable via application.properties ──────────────────────
    @Value("${app.init.admin.email:admin@navigation.tn}")
    private String adminEmail;

    @Value("${app.init.admin.password:Admin@1234}")
    private String adminPassword;

    @Value("${app.init.admin.firstName:Super}")
    private String adminFirstName;

    @Value("${app.init.admin.lastName:Admin}")
    private String adminLastName;

    @Value("${app.init.admin.matricule:ADM-001}")
    private String adminMatricule;
    // ─────────────────────────────────────────────────────────────────

    @Override
    public void run(String... args) {

        // 1. S'assurer que le rôle ADMIN existe
        Role adminRole = roleRepository.findByName(ERole.ADMIN)
                .orElseGet(() -> {
                    Role r = new Role(ERole.ADMIN);
                    roleRepository.save(r);
                    log.info("DataInitializer — Rôle ADMIN créé.");
                    return r;
                });

        // 2. Créer l'admin seulement s'il n'existe pas déjà
        if (userRepository.existsByEmail(adminEmail)) {
            log.info("DataInitializer — Admin déjà existant : {}", adminEmail);
            return;
        }

        User admin = User.builder()
                .firstName(adminFirstName)
                .lastName(adminLastName)
                .email(adminEmail)
                .password(passwordEncoder.encode(adminPassword))
                .matricule(adminMatricule)
                .grade("Administrateur Système")
                .serviceNom("Administration")
                .active(true)
                .roles(Set.of(adminRole))
                .build();

        userRepository.save(admin);

        log.info("====================================================");
        log.info("  PREMIER ADMIN CRÉÉ AVEC SUCCÈS");
        log.info("  Email    : {}", adminEmail);
        log.info("  Password : {}", adminPassword);
        log.info("  CHANGEZ CE MOT DE PASSE APRÈS CONNEXION !");
        log.info("====================================================");
    }
}
