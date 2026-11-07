package com.air.navigation.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ResetPasswordRequestDto {

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Format email invalide")
    private String email;

    /** Code à 6 chiffres reçu par e-mail */
    @NotBlank(message = "Le code de vérification est obligatoire")
    @Pattern(regexp = "^\\d{6}$", message = "Le code doit contenir 6 chiffres")
    @JsonAlias("resetToken")
    private String code;

    @NotBlank(message = "Le nouveau mot de passe est obligatoire")
    @Pattern(regexp = ValidationPatterns.STRONG_PASSWORD,
             message = "Le mot de passe doit contenir au moins 8 caractères, une majuscule, une minuscule, un chiffre et un caractère spécial")
    private String newPassword;
}
