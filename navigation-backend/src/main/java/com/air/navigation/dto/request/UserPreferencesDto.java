package com.air.navigation.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

/** Préférences de l'utilisateur connecté (lecture et mise à jour) */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferencesDto {

    @Pattern(regexp = "^(fr|en)$", message = "Langue non prise en charge (fr ou en)")
    private String language;

    @Size(max = 50, message = "Fuseau horaire invalide")
    @Pattern(regexp = "^[A-Za-z_]+(/[A-Za-z_\\-]+)*$", message = "Fuseau horaire invalide")
    private String timezone;

    private Boolean emailNotifications;
    private Boolean browserNotifications;
    private Boolean notificationSounds;

    @Min(value = 0, message = "Durée de session invalide")
    @Max(value = 480, message = "Durée de session invalide")
    private Integer sessionTimeoutMinutes;

    private Boolean twoFactorEnabled;

    /** Lecture seule : l'envoi d'e-mails est-il opérationnel (condition pour activer la 2FA) */
    private Boolean mailConfigured;
}
