package com.air.navigation.services.impl;

import com.air.navigation.dto.request.CreateUserRequestDto;
import com.air.navigation.dto.response.UserResponseDto;
import com.air.navigation.entities.Role;
import com.air.navigation.entities.User;
import com.air.navigation.enums.ERole;
import com.air.navigation.repository.RoleRepository;
import com.air.navigation.repository.UserRepository;
import com.air.navigation.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.air.navigation.mail.MailService mailService;

    /** Nom de l'agent connecté (auteur de la modification, pour l'e-mail) */
    private String currentActorName() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.air.navigation.security.services.UserDetailsImpl u) {
            return u.getFullName();
        }
        return null;
    }

    private static List<String> roleNames(Set<Role> roles) {
        return roles.stream().map(r -> r.getName().name()).sorted().collect(Collectors.toList());
    }

    private static void diff(List<String[]> changes, String label, String before, String after) {
        String b = before == null ? "" : before.trim();
        String a = after == null ? "" : after.trim();
        if (!b.equals(a)) changes.add(new String[]{label, b, a});
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponseDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponseDto getUserById(Long id) {
        return toResponseDto(findUserOrThrow(id));
    }

    @Override
    @Transactional
    public UserResponseDto createUser(CreateUserRequestDto dto) {
        String email = dto.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Un compte existe déjà avec cet email : " + email);
        }
        if (notBlank(dto.getBadgeNumber()) && userRepository.existsByBadgeNumber(dto.getBadgeNumber().trim())) {
            throw new IllegalArgumentException("Ce numéro de badge est déjà attribué : " + dto.getBadgeNumber());
        }
        if (notBlank(dto.getMatricule()) && userRepository.existsByMatricule(dto.getMatricule().trim())) {
            throw new IllegalArgumentException("Ce matricule est déjà attribué : " + dto.getMatricule());
        }
        Set<Role> roles = resolveRoles(dto.getRoles());
        User user = User.builder()
                .firstName(dto.getFirstName().trim())
                .lastName(dto.getLastName().trim())
                .email(email)
                .password(passwordEncoder.encode(dto.getPassword()))
                .phoneNumber(trimToNull(dto.getPhoneNumber()))
                .badgeNumber(trimToNull(dto.getBadgeNumber()))
                .matricule(trimToNull(dto.getMatricule()))
                .grade(trimToNull(dto.getGrade()))
                .serviceNom(trimToNull(dto.getServiceNom()))
                .active(dto.getActive() == null || dto.getActive())
                .roles(roles)
                .build();
        User saved = userRepository.save(user);
        log.info("User created: {} ({})", saved.getFullName(), saved.getEmail());

        if (dto.getSendWelcomeEmail() == null || dto.getSendWelcomeEmail()) {
            mailService.sendWelcome(saved.getEmail(), saved.getFirstName(), dto.getPassword(), roleNames(roles));
        }
        return toResponseDto(saved);
    }

    @Override
    @Transactional
    public UserResponseDto updateUser(Long id, com.air.navigation.dto.request.UpdateUserRequestDto dto, boolean asAdmin) {
        User user = findUserOrThrow(id);
        String email = dto.getEmail().trim().toLowerCase();
        userRepository.findByEmail(email).filter(u -> !u.getId().equals(id)).ifPresent(u -> {
            throw new IllegalArgumentException("Un autre compte utilise déjà cet email : " + email);
        });
        // ── Récapitulatif des changements pour l'e-mail ──
        List<String[]> changes = new java.util.ArrayList<>();
        String oldEmail = user.getEmail();
        boolean oldActive = user.isActive();
        diff(changes, "Prénom", user.getFirstName(), dto.getFirstName());
        diff(changes, "Nom", user.getLastName(), dto.getLastName());
        diff(changes, "Adresse e-mail", user.getEmail(), email);
        diff(changes, "Téléphone", user.getPhoneNumber(), dto.getPhoneNumber());
        if (asAdmin) {
            diff(changes, "N° de badge", user.getBadgeNumber(), dto.getBadgeNumber());
            diff(changes, "Matricule", user.getMatricule(), dto.getMatricule());
            diff(changes, "Grade", user.getGrade(), dto.getGrade());
            diff(changes, "Service", user.getServiceNom(), dto.getServiceNom());
            if (dto.getRoles() != null && !dto.getRoles().isEmpty()) {
                String before = String.join(", ", roleNames(user.getRoles()));
                String after = dto.getRoles().stream().map(Enum::name).sorted().collect(Collectors.joining(", "));
                diff(changes, "Rôle", before, after);
            }
            if (dto.getActive() != null && dto.getActive() != user.isActive()) {
                changes.add(new String[]{"Statut du compte", user.isActive() ? "Actif" : "Désactivé", dto.getActive() ? "Actif" : "Désactivé"});
            }
        }
        boolean passwordChanged = notBlank(dto.getPassword());
        if (passwordChanged) changes.add(new String[]{"Mot de passe", "••••••••", "Nouveau mot de passe défini"});

        user.setFirstName(dto.getFirstName().trim());
        user.setLastName(dto.getLastName().trim());
        user.setEmail(email);
        user.setPhoneNumber(trimToNull(dto.getPhoneNumber()));
        if (passwordChanged) {
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
        // Champs administratifs : uniquement modifiables par un administrateur
        if (asAdmin) {
            String badge = trimToNull(dto.getBadgeNumber());
            if (badge != null && !badge.equals(user.getBadgeNumber()) && userRepository.existsByBadgeNumber(badge)) {
                throw new IllegalArgumentException("Ce numéro de badge est déjà attribué : " + badge);
            }
            String matricule = trimToNull(dto.getMatricule());
            if (matricule != null && !matricule.equals(user.getMatricule()) && userRepository.existsByMatricule(matricule)) {
                throw new IllegalArgumentException("Ce matricule est déjà attribué : " + matricule);
            }
            user.setBadgeNumber(badge);
            user.setMatricule(matricule);
            user.setGrade(trimToNull(dto.getGrade()));
            user.setServiceNom(trimToNull(dto.getServiceNom()));
            if (dto.getActive() != null) user.setActive(dto.getActive());
            if (dto.getRoles() != null && !dto.getRoles().isEmpty()) {
                user.setRoles(resolveRoles(dto.getRoles()));
            }
        }
        User saved = userRepository.save(user);

        boolean notify = dto.getNotifyUser() == null || dto.getNotifyUser();
        if (notify && !changes.isEmpty()) {
            // Le mot de passe n'est communiqué que s'il a été défini par un administrateur pour un autre compte
            String actor = currentActorName();
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            boolean selfEdit = auth != null && auth.getPrincipal() instanceof com.air.navigation.security.services.UserDetailsImpl u
                    && u.getId().equals(saved.getId());
            String pwdForMail = passwordChanged && asAdmin && !selfEdit ? dto.getPassword() : null;
            mailService.sendAccountUpdated(saved.getEmail(), saved.getFirstName(), changes, pwdForMail, actor);
            if (!oldEmail.equalsIgnoreCase(saved.getEmail())) {
                // Alerte de sécurité sur l'ancienne adresse
                mailService.sendAccountUpdated(oldEmail, saved.getFirstName(),
                        List.<String[]>of(new String[]{"Adresse e-mail", oldEmail, saved.getEmail()}), null, actor);
            }
            if (oldActive != saved.isActive()) {
                mailService.sendAccountStatus(saved.getEmail(), saved.getFirstName(), saved.isActive());
            }
        }
        log.info("User updated: {} — {} modification(s)", saved.getEmail(), changes.size());
        return toResponseDto(saved);
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    private static String trimToNull(String s) { return notBlank(s) ? s.trim() : null; }

    /**
     * Renvoie les identifiants : le mot de passe d'origine n'étant jamais stocké en clair,
     * un nouveau mot de passe temporaire est généré puis envoyé par e-mail.
     */
    @Override
    @Transactional
    public java.util.Map<String, Object> resendCredentials(Long id) {
        User user = findUserOrThrow(id);
        if (!user.isActive()) {
            throw new IllegalArgumentException("Le compte est désactivé : réactivez-le avant de renvoyer les identifiants.");
        }
        String tempPassword = generateTemporaryPassword();
        user.setPassword(passwordEncoder.encode(tempPassword));
        userRepository.save(user);
        mailService.sendWelcome(user.getEmail(), user.getFirstName(), tempPassword, roleNames(user.getRoles()));
        log.info("Identifiants renvoyés à {}", user.getEmail());

        java.util.Map<String, Object> res = new java.util.LinkedHashMap<>();
        res.put("smtpConfigured", mailService.isSmtpConfigured());
        res.put("message", mailService.isSmtpConfigured()
                ? "Nouveaux identifiants envoyés à " + user.getEmail() + "."
                : "SMTP non configuré : l'e-mail a été enregistré dans le dossier mail-outbox du backend (non envoyé).");
        return res;
    }

    private static final String PWD_UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String PWD_LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String PWD_DIGIT = "23456789";
    private static final String PWD_SPECIAL = "!@#$%&*?-_";

    /** 12 caractères : au moins une majuscule, une minuscule, un chiffre et un caractère spécial */
    private static String generateTemporaryPassword() {
        java.security.SecureRandom rnd = new java.security.SecureRandom();
        String all = PWD_UPPER + PWD_LOWER + PWD_DIGIT + PWD_SPECIAL;
        List<Character> chars = new java.util.ArrayList<>();
        for (String set : List.of(PWD_UPPER, PWD_LOWER, PWD_DIGIT, PWD_SPECIAL)) chars.add(set.charAt(rnd.nextInt(set.length())));
        while (chars.size() < 12) chars.add(all.charAt(rnd.nextInt(all.length())));
        java.util.Collections.shuffle(chars, rnd);
        StringBuilder sb = new StringBuilder();
        chars.forEach(sb::append);
        return sb.toString();
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new RuntimeException("Utilisateur introuvable avec l'ID : " + id);
        }
        userRepository.deleteById(id);
        log.info("User deleted with ID: {}", id);
    }

    @Override
    @Transactional
    public UserResponseDto toggleUserStatus(Long id) {
        User user = findUserOrThrow(id);
        user.setActive(!user.isActive());
        User saved = userRepository.save(user);
        mailService.sendAccountStatus(saved.getEmail(), saved.getFirstName(), saved.isActive());
        return toResponseDto(saved);
    }

    private User findUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable avec l'ID : " + id));
    }

    private Set<Role> resolveRoles(Set<ERole> roleEnums) {
        Set<Role> roles = new HashSet<>();
        if (roleEnums == null || roleEnums.isEmpty()) {
            Role defaultRole = roleRepository.findByName(ERole.ATC)
                    .orElseGet(() -> roleRepository.save(new Role(ERole.ATC)));
            roles.add(defaultRole);
        } else {
            roleEnums.forEach(eRole -> {
                Role role = roleRepository.findByName(eRole)
                        .orElseGet(() -> roleRepository.save(new Role(eRole)));
                roles.add(role);
            });
        }
        return roles;
    }

    public UserResponseDto toResponseDto(User user) {
        List<String> roleNames = user.getRoles().stream()
                .map(r -> r.getName().name())
                .collect(Collectors.toList());
        return UserResponseDto.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .badgeNumber(user.getBadgeNumber())
                .matricule(user.getMatricule())
                .grade(user.getGrade())
                .serviceNom(user.getServiceNom())
                .active(user.isActive())
                .roles(roleNames)
                .createdAt(user.getCreatedAt())
                .build();
    }
}
