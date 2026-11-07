package com.air.navigation.services.impl;

import com.air.navigation.dto.request.ChangePasswordRequestDto;
import com.air.navigation.dto.request.LoginRequestDto;
import com.air.navigation.dto.request.ResetPasswordRequestDto;
import com.air.navigation.dto.response.JwtResponseDto;
import com.air.navigation.dto.response.MessageResponseDto;
import com.air.navigation.entities.User;
import com.air.navigation.mail.MailService;
import com.air.navigation.repository.UserRepository;
import com.air.navigation.security.jwt.JwtUtils;
import com.air.navigation.security.services.UserDetailsImpl;
import com.air.navigation.services.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String GENERIC_FORGOT_MESSAGE =
            "Si un compte actif correspond à cette adresse, un code de vérification vient d'être envoyé par e-mail.";

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;

    @Value("${app.reset.code-validity-minutes:15}")
    private int codeValidityMinutes;

    @Override
    @Transactional
    public JwtResponseDto login(LoginRequestDto request) {
        String email = request.getEmail().trim().toLowerCase();
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.getPassword()));

        // Un compte désactivé ne peut pas se connecter
        User account = userRepository.findByEmail(email).orElseThrow();
        if (!account.isActive()) {
            throw new DisabledException("Ce compte est désactivé. Contactez votre administrateur.");
        }

        // Double authentification : mot de passe validé → code envoyé par e-mail, pas encore de jeton
        if (account.isTwoFactorOn()) {
            String code = String.format("%06d", RANDOM.nextInt(1_000_000));
            account.setTwoFactorCode(passwordEncoder.encode(code));
            account.setTwoFactorExpiry(LocalDateTime.now().plusMinutes(10));
            account.setTwoFactorAttempts(0);
            userRepository.save(account);
            mailService.sendTwoFactorCode(account.getEmail(), account.getFirstName(), code, 10);
            log.info("[AUTH] Code 2FA envoyé à {}", email);
            return JwtResponseDto.twoFactorChallenge(account.getEmail());
        }

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

    /** Étape 2 de la connexion avec double authentification */
    @Override
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public JwtResponseDto verifyTwoFactor(String rawEmail, String code) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Code invalide ou expiré."));
        if (!user.isActive()) throw new DisabledException("Ce compte est désactivé.");
        if (user.getTwoFactorCode() == null || user.getTwoFactorExpiry() == null) {
            throw new IllegalArgumentException("Aucune connexion en attente. Reconnectez-vous.");
        }
        if (user.getTwoFactorExpiry().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Ce code a expiré. Reconnectez-vous pour en recevoir un nouveau.");
        }
        int attempts = user.getTwoFactorAttempts() == null ? 0 : user.getTwoFactorAttempts();
        if (attempts >= MAX_ATTEMPTS) {
            throw new IllegalArgumentException("Trop de tentatives. Reconnectez-vous pour recevoir un nouveau code.");
        }
        if (code == null || !passwordEncoder.matches(code, user.getTwoFactorCode())) {
            user.setTwoFactorAttempts(attempts + 1);
            userRepository.save(user);
            int left = MAX_ATTEMPTS - attempts - 1;
            throw new IllegalArgumentException(left > 0 ? "Code incorrect. " + left + " tentative(s) restante(s)."
                    : "Trop de tentatives. Reconnectez-vous pour recevoir un nouveau code.");
        }
        user.setTwoFactorCode(null);
        user.setTwoFactorExpiry(null);
        user.setTwoFactorAttempts(null);
        userRepository.save(user);

        UserDetailsImpl details = UserDetailsImpl.build(user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
        String jwt = jwtUtils.generateJwtToken(authentication);
        List<String> roles = details.getAuthorities().stream().map(a -> a.getAuthority()).collect(Collectors.toList());
        log.info("[AUTH] Connexion 2FA réussie : {}", email);
        return new JwtResponseDto(jwt, details.getId(), details.getUsername(), details.getFullName(), roles);
    }

    // ══════════════════════════════════════════════════════
    // MOT DE PASSE OUBLIÉ — code à 6 chiffres par e-mail
    // ══════════════════════════════════════════════════════

    @Override
    @Transactional
    public MessageResponseDto forgotPassword(String rawEmail) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase();
        Optional<User> found = userRepository.findByEmail(email).filter(User::isActive);
        if (found.isEmpty()) {
            // Réponse identique : on ne révèle pas si l'adresse existe
            log.info("[AUTH] Demande de réinitialisation pour une adresse inconnue ou inactive : {}", email);
            return new MessageResponseDto(GENERIC_FORGOT_MESSAGE);
        }
        User user = found.get();

        // Anti-spam : un nouveau code au plus toutes les 60 secondes
        if (user.getResetTokenExpiry() != null
                && user.getResetTokenExpiry().minusMinutes(codeValidityMinutes).isAfter(LocalDateTime.now().minusSeconds(60))) {
            return new MessageResponseDto(GENERIC_FORGOT_MESSAGE);
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        user.setResetPasswordToken(passwordEncoder.encode(code)); // jamais stocké en clair
        user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(codeValidityMinutes));
        user.setResetAttempts(0);
        userRepository.save(user);

        mailService.sendResetCode(user.getEmail(), user.getFirstName(), code, codeValidityMinutes);
        log.info("[AUTH] Code de réinitialisation envoyé à {}", email);
        return new MessageResponseDto(GENERIC_FORGOT_MESSAGE);
    }

    @Override
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public MessageResponseDto verifyResetCode(String rawEmail, String code) {
        checkCode(rawEmail, code);
        return new MessageResponseDto("Code valide. Vous pouvez choisir un nouveau mot de passe.");
    }

    @Override
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public MessageResponseDto resetPassword(ResetPasswordRequestDto request) {
        User user = checkCode(request.getEmail(), request.getCode());
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Le nouveau mot de passe doit être différent de l'ancien.");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setResetPasswordToken(null);
        user.setResetTokenExpiry(null);
        user.setResetAttempts(null);
        userRepository.save(user);

        mailService.sendPasswordChanged(user.getEmail(), user.getFirstName());
        log.info("[AUTH] Mot de passe réinitialisé pour {}", user.getEmail());
        return new MessageResponseDto("Mot de passe réinitialisé avec succès. Vous pouvez vous connecter.");
    }

    /** Vérifie le code : existence, expiration, nombre d'essais. Incrémente le compteur en cas d'échec. */
    private User checkCode(String rawEmail, String code) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Code invalide ou expiré."));

        if (user.getResetPasswordToken() == null || user.getResetTokenExpiry() == null) {
            throw new IllegalArgumentException("Aucune demande en cours. Demandez un nouveau code.");
        }
        if (user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Ce code a expiré. Demandez un nouveau code.");
        }
        int attempts = user.getResetAttempts() == null ? 0 : user.getResetAttempts();
        if (attempts >= MAX_ATTEMPTS) {
            throw new IllegalArgumentException("Trop de tentatives. Demandez un nouveau code.");
        }
        if (code == null || !passwordEncoder.matches(code, user.getResetPasswordToken())) {
            user.setResetAttempts(attempts + 1);
            userRepository.save(user);
            int left = MAX_ATTEMPTS - attempts - 1;
            throw new IllegalArgumentException(left > 0
                    ? "Code incorrect. " + left + " tentative(s) restante(s)."
                    : "Trop de tentatives. Demandez un nouveau code.");
        }
        return user;
    }

    @Override
    @Transactional
    public MessageResponseDto changePassword(Long userId, ChangePasswordRequestDto request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable : " + userId));
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Mot de passe actuel incorrect");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        mailService.sendPasswordChanged(user.getEmail(), user.getFirstName());
        log.info("Password changed for user: {}", user.getEmail());
        return new MessageResponseDto("Mot de passe modifié avec succès");
    }
}
