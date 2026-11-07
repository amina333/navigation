package com.air.navigation.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ForgotPasswordRequestDto {

    @NotBlank(message = "L'email est obligatoire")
    @Email(regexp = ValidationPatterns.EMAIL, message = "Format email invalide")
    @Size(max = 100, message = "Email trop long")
    private String email;

    /** Utilisé seulement par /verify-reset-code */
    @Pattern(regexp = "^$|^\\d{6}$", message = "Le code doit contenir 6 chiffres")
    private String code;
}
