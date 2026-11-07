package com.air.navigation.services;

import com.air.navigation.dto.request.ChangePasswordRequestDto;
import com.air.navigation.dto.request.LoginRequestDto;
import com.air.navigation.dto.request.ResetPasswordRequestDto;
import com.air.navigation.dto.response.JwtResponseDto;
import com.air.navigation.dto.response.MessageResponseDto;

public interface AuthService {
    JwtResponseDto login(LoginRequestDto request);

    /** Validation du code de double authentification reçu par e-mail */
    JwtResponseDto verifyTwoFactor(String email, String code);

    /** Envoie un code à 6 chiffres par e-mail (réponse identique que le compte existe ou non) */
    MessageResponseDto forgotPassword(String email);

    /** Vérifie un code sans le consommer (étape intermédiaire de l'interface) */
    MessageResponseDto verifyResetCode(String email, String code);

    MessageResponseDto resetPassword(ResetPasswordRequestDto request);

    MessageResponseDto changePassword(Long userId, ChangePasswordRequestDto request);
}
