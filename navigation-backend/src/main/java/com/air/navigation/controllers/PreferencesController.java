package com.air.navigation.controllers;

import com.air.navigation.dto.request.UserPreferencesDto;
import com.air.navigation.entities.User;
import com.air.navigation.mail.MailService;
import com.air.navigation.repository.UserRepository;
import com.air.navigation.security.services.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.util.Set;

/**
 * Préférences de l'utilisateur connecté : langue, fuseau horaire, notifications, sécurité.
 */
@RestController
@RequestMapping("/api/me/preferences")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Préférences", description = "Paramètres personnels de l'agent")
@Slf4j
public class PreferencesController {

    public static final String DEFAULT_LANGUAGE = "fr";
    public static final String DEFAULT_TIMEZONE = "Africa/Tunis";
    public static final int DEFAULT_TIMEOUT = 30;
    private static final Set<Integer> ALLOWED_TIMEOUTS = Set.of(0, 15, 30, 60, 120);

    private final UserRepository userRepository;
    private final MailService mailService;

    @GetMapping
    @Operation(summary = "Mes préférences")
    @Transactional(readOnly = true)
    public ResponseEntity<UserPreferencesDto> get(@AuthenticationPrincipal UserDetailsImpl me) {
        return ResponseEntity.ok(toDto(find(me)));
    }

    @PutMapping
    @Operation(summary = "Mettre à jour mes préférences (seuls les champs fournis sont modifiés)")
    @Transactional
    public ResponseEntity<UserPreferencesDto> update(@Valid @RequestBody UserPreferencesDto dto,
                                                     @AuthenticationPrincipal UserDetailsImpl me) {
        User u = find(me);
        if (dto.getLanguage() != null) u.setLanguage(dto.getLanguage());
        if (dto.getTimezone() != null) {
            try {
                ZoneId.of(dto.getTimezone());
            } catch (Exception e) {
                throw new IllegalArgumentException("Fuseau horaire inconnu : " + dto.getTimezone());
            }
            u.setTimezone(dto.getTimezone());
        }
        if (dto.getEmailNotifications() != null) u.setEmailNotifications(dto.getEmailNotifications());
        if (dto.getBrowserNotifications() != null) u.setBrowserNotifications(dto.getBrowserNotifications());
        if (dto.getNotificationSounds() != null) u.setNotificationSounds(dto.getNotificationSounds());
        if (dto.getSessionTimeoutMinutes() != null) {
            if (!ALLOWED_TIMEOUTS.contains(dto.getSessionTimeoutMinutes())) {
                throw new IllegalArgumentException("Durée de session non autorisée");
            }
            u.setSessionTimeoutMinutes(dto.getSessionTimeoutMinutes());
        }
        if (dto.getTwoFactorEnabled() != null) {
            // Sans envoi d'e-mail opérationnel, activer la 2FA bloquerait l'accès au compte
            if (dto.getTwoFactorEnabled() && !mailService.isSmtpConfigured()) {
                throw new IllegalArgumentException("Impossible d'activer la double authentification : l'envoi d'e-mails n'est pas configuré sur le serveur.");
            }
            u.setTwoFactorEnabled(dto.getTwoFactorEnabled());
            log.info("[SECURITE] 2FA {} pour {}", dto.getTwoFactorEnabled() ? "activée" : "désactivée", u.getEmail());
        }
        return ResponseEntity.ok(toDto(userRepository.save(u)));
    }

    private User find(UserDetailsImpl me) {
        return userRepository.findById(me.getId())
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable"));
    }

    private UserPreferencesDto toDto(User u) {
        return UserPreferencesDto.builder()
                .language(u.getLanguage() != null ? u.getLanguage() : DEFAULT_LANGUAGE)
                .timezone(u.getTimezone() != null ? u.getTimezone() : DEFAULT_TIMEZONE)
                .emailNotifications(!Boolean.FALSE.equals(u.getEmailNotifications()))
                .browserNotifications(!Boolean.FALSE.equals(u.getBrowserNotifications()))
                .notificationSounds(!Boolean.FALSE.equals(u.getNotificationSounds()))
                .sessionTimeoutMinutes(u.getSessionTimeoutMinutes() != null ? u.getSessionTimeoutMinutes() : DEFAULT_TIMEOUT)
                .twoFactorEnabled(u.isTwoFactorOn())
                .mailConfigured(mailService.isSmtpConfigured())
                .build();
    }
}
