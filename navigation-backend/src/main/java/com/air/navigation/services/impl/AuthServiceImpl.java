package com.air.navigation.services.impl;

import com.air.navigation.dto.request.ChangePasswordRequestDto;
import com.air.navigation.dto.request.LoginRequestDto;
import com.air.navigation.dto.request.ResetPasswordRequestDto;
import com.air.navigation.dto.response.JwtResponseDto;
import com.air.navigation.dto.response.MessageResponseDto;
import com.air.navigation.entities.User;
import com.air.navigation.repository.UserRepository;
import com.air.navigation.security.jwt.JwtUtils;
import com.air.navigation.security.services.UserDetailsImpl;
import com.air.navigation.services.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public JwtResponseDto login(LoginRequestDto request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        List<String> roles = userDetails.getAuthorities().stream()
                .map(item -> item.getAuthority())
                .collect(Collectors.toList());

        log.info("User {} logged in successfully", userDetails.getUsername());

        return new JwtResponseDto(jwt, userDetails.getId(), userDetails.getUsername(),
                userDetails.getFullName(), roles);
    }

    @Override
    @Transactional
    public MessageResponseDto generateResetToken(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé : " + email));
        String token = UUID.randomUUID().toString();
        user.setResetPasswordToken(token);
        userRepository.save(user);
        log.info("Reset token generated for user: {}", email);
        return new MessageResponseDto("Token de réinitialisation généré : " + token);
    }

    @Override
    @Transactional
    public MessageResponseDto resetPassword(ResetPasswordRequestDto request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        if (user.getResetPasswordToken() == null ||
                !user.getResetPasswordToken().equals(request.getResetToken())) {
            throw new RuntimeException("Token de réinitialisation invalide");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setResetPasswordToken(null);
        userRepository.save(user);
        log.info("Password reset successful for: {}", request.getEmail());
        return new MessageResponseDto("Mot de passe réinitialisé avec succès");
    }
    @Override
    @Transactional
    public MessageResponseDto changePassword(Long userId, ChangePasswordRequestDto request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable : " + userId));
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new RuntimeException("Mot de passe actuel incorrect");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("Password changed for user: {}", user.getEmail());
        return new MessageResponseDto("Mot de passe modifié avec succès");
    }
}
