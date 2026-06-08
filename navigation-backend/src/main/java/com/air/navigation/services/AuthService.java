package com.air.navigation.services;

import com.air.navigation.dto.request.ChangePasswordRequestDto;
import com.air.navigation.dto.request.LoginRequestDto;
import com.air.navigation.dto.request.ResetPasswordRequestDto;
import com.air.navigation.dto.response.JwtResponseDto;
import com.air.navigation.dto.response.MessageResponseDto;

public interface AuthService {
    JwtResponseDto login(LoginRequestDto request);
    MessageResponseDto generateResetToken(String email);
    MessageResponseDto resetPassword(ResetPasswordRequestDto request);
    MessageResponseDto changePassword(Long userId, ChangePasswordRequestDto request);
}

