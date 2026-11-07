package com.air.navigation.dto.request;

import com.air.navigation.enums.ERole;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.Set;

@Data
public class CreateUserRequestDto {

    @NotBlank(message = "Le prénom est obligatoire")
    @Size(min = 2, max = 50, message = "Le prénom doit contenir entre 2 et 50 caractères")
    @Pattern(regexp = ValidationPatterns.PERSON_NAME, message = "Le prénom contient des caractères non autorisés")
    private String firstName;

    @NotBlank(message = "Le nom est obligatoire")
    @Size(min = 2, max = 50, message = "Le nom doit contenir entre 2 et 50 caractères")
    @Pattern(regexp = ValidationPatterns.PERSON_NAME, message = "Le nom contient des caractères non autorisés")
    private String lastName;

    @NotBlank(message = "L'email est obligatoire")
    @Email(regexp = ValidationPatterns.EMAIL, message = "Format email invalide")
    @Size(max = 100, message = "L'email ne doit pas dépasser 100 caractères")
    private String email;

    @NotBlank(message = "Le mot de passe est obligatoire")
    @Pattern(regexp = ValidationPatterns.STRONG_PASSWORD,
             message = "Le mot de passe doit contenir au moins 8 caractères, une majuscule, une minuscule, un chiffre et un caractère spécial")
    private String password;

    @Pattern(regexp = ValidationPatterns.PHONE_OR_EMPTY, message = "Numéro de téléphone invalide")
    private String phoneNumber;

    @Size(max = 30, message = "Le badge ne doit pas dépasser 30 caractères")
    @Pattern(regexp = ValidationPatterns.CODE_OR_EMPTY, message = "Badge : lettres, chiffres et tirets uniquement")
    private String badgeNumber;

    @Size(max = 30, message = "Le matricule ne doit pas dépasser 30 caractères")
    @Pattern(regexp = ValidationPatterns.CODE_OR_EMPTY, message = "Matricule : lettres, chiffres et tirets uniquement")
    private String matricule;

    @Size(max = 60, message = "Le grade ne doit pas dépasser 60 caractères")
    @Pattern(regexp = ValidationPatterns.SAFE_TEXT, message = "Le grade contient des caractères non autorisés")
    private String grade;

    @Size(max = 80, message = "Le service ne doit pas dépasser 80 caractères")
    @Pattern(regexp = ValidationPatterns.SAFE_TEXT, message = "Le service contient des caractères non autorisés")
    private String serviceNom;

    @NotEmpty(message = "Au moins un rôle est obligatoire")
    private Set<ERole> roles;

    private Boolean active;

    /** Envoyer l'e-mail de bienvenue avec les identifiants (défaut : oui) */
    private Boolean sendWelcomeEmail;
}
