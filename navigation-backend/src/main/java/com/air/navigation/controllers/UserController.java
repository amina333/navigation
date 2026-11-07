package com.air.navigation.controllers;

import com.air.navigation.dto.request.CreateUserRequestDto;
import com.air.navigation.dto.response.UserResponseDto;
import com.air.navigation.services.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Utilisateurs", description = "Gestion des agents et des rôles")
public class UserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "Liste de tous les utilisateurs")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERVISEUR')")
    public ResponseEntity<List<UserResponseDto>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Récupérer un utilisateur par ID")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERVISEUR') or #id == authentication.principal.id")
    public ResponseEntity<UserResponseDto> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PostMapping
    @Operation(summary = "Créer un utilisateur (Admin)")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<UserResponseDto> createUser(@Valid @RequestBody CreateUserRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(dto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Mettre à jour un utilisateur")
    @PreAuthorize("hasAuthority('ADMIN') or #id == authentication.principal.id")
    public ResponseEntity<UserResponseDto> updateUser(@PathVariable Long id,
                                                      @Valid @RequestBody com.air.navigation.dto.request.UpdateUserRequestDto dto,
                                                      org.springframework.security.core.Authentication authentication) {
        // Un agent peut modifier son propre profil ; rôles / statut / matricule restent réservés à l'ADMIN
        boolean isAdmin = authentication.getAuthorities().stream().anyMatch(a -> "ADMIN".equals(a.getAuthority()));
        return ResponseEntity.ok(userService.updateUser(id, dto, isAdmin));
    }

    @PostMapping("/{id}/resend-credentials")
    @Operation(summary = "Renvoyer les identifiants (nouveau mot de passe temporaire par e-mail)")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<java.util.Map<String, Object>> resendCredentials(@PathVariable Long id) {
        return ResponseEntity.ok(userService.resendCredentials(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un utilisateur (Admin)")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle-status")
    @Operation(summary = "Activer/Désactiver un utilisateur")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<UserResponseDto> toggleStatus(@PathVariable Long id) {
        return ResponseEntity.ok(userService.toggleUserStatus(id));
    }
}
