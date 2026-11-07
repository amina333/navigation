package com.air.navigation.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "email")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "badge_number")
    private String badgeNumber;

    // ── Champs UML : Matricule, Grade, Service ────────────────────────
    @Column(name = "matricule", unique = true)
    private String matricule;

    @Column(name = "grade")
    private String grade;

    @Column(name = "service_nom")
    private String serviceNom;
    // ─────────────────────────────────────────────────────────────────

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /** Empreinte BCrypt du code de réinitialisation (jamais le code en clair) */
    @Column(name = "reset_password_token")
    private String resetPasswordToken;

    @Column(name = "reset_token_expiry")
    private LocalDateTime resetTokenExpiry;

    /** Tentatives de saisie du code (blocage après 5 essais) */
    @Column(name = "reset_attempts")
    private Integer resetAttempts;

    // ── Préférences utilisateur (page Paramètres) ─────────────────────
    /** fr | en */
    @Column(name = "pref_language", length = 5)
    private String language;

    /** Identifiant IANA, ex. Africa/Tunis */
    @Column(name = "pref_timezone", length = 50)
    private String timezone;

    @Column(name = "pref_email_notifications")
    private Boolean emailNotifications;

    @Column(name = "pref_browser_notifications")
    private Boolean browserNotifications;

    @Column(name = "pref_notification_sounds")
    private Boolean notificationSounds;

    /** Déconnexion après inactivité (minutes, 0 = jamais) */
    @Column(name = "pref_session_timeout")
    private Integer sessionTimeoutMinutes;

    // ── Double authentification par e-mail ────────────────────────────
    @Column(name = "two_factor_enabled")
    private Boolean twoFactorEnabled;

    /** Empreinte BCrypt du code 2FA */
    @Column(name = "two_factor_code")
    private String twoFactorCode;

    @Column(name = "two_factor_expiry")
    private LocalDateTime twoFactorExpiry;

    @Column(name = "two_factor_attempts")
    private Integer twoFactorAttempts;

    public boolean isTwoFactorOn() { return Boolean.TRUE.equals(twoFactorEnabled); }

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    @Builder.Default
    @JsonIgnore
    private Set<Role> roles = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
