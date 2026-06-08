package com.air.navigation.controllers;

import com.air.navigation.dto.request.ChangePasswordRequestDto;
import com.air.navigation.dto.request.LoginRequestDto;
import com.air.navigation.dto.request.ResetPasswordRequestDto;
import com.air.navigation.dto.response.JwtResponseDto;
import com.air.navigation.dto.response.MessageResponseDto;
import com.air.navigation.security.services.UserDetailsImpl;
import com.air.navigation.services.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@Tag(name = "Authentification", description = "Connexion et réinitialisation de mot de passe")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signin")
    @Operation(summary = "Connexion — retourne un token JWT")
    public ResponseEntity<JwtResponseDto> login(@Valid @RequestBody LoginRequestDto request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/generate-reset-token/{email}")
    @Operation(summary = "Générer un token de réinitialisation")
    public ResponseEntity<MessageResponseDto> generateResetToken(@PathVariable String email) {
        return ResponseEntity.ok(authService.generateResetToken(email));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Réinitialiser le mot de passe via token")
    public ResponseEntity<MessageResponseDto> resetPassword(@Valid @RequestBody ResetPasswordRequestDto request) {
        return ResponseEntity.ok(authService.resetPassword(request));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Changer le mot de passe (utilisé connecté)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<MessageResponseDto> changePassword(
            @Valid @RequestBody ChangePasswordRequestDto request,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(authService.changePassword(currentUser.getId(), request));
    }
}
