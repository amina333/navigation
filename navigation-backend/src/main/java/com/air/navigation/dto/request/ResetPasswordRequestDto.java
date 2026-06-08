package com.air.navigation.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResetPasswordRequestDto {

    @NotBlank @Email
    private String email;

    @NotBlank
    private String resetToken;

    @NotBlank
    private String newPassword;
}
