package com.air.navigation.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ChangePasswordRequestDto {

    @NotBlank(message = "L'ancien mot de passe est obligatoire")
    private String currentPassword;

    @NotBlank(message = "Le nouveau mot de passe est obligatoire")
    @Pattern(regexp = ValidationPatterns.STRONG_PASSWORD,
             message = "Le mot de passe doit contenir au moins 8 caractères, une majuscule, une minuscule, un chiffre et un caractère spécial")
    private String newPassword;

    @JsonIgnore
    @AssertTrue(message = "Le nouveau mot de passe doit être différent de l'ancien")
    public boolean isDifferentFromCurrent() {
        return currentPassword == null || newPassword == null || !currentPassword.equals(newPassword);
    }
}
