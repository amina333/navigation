package com.air.navigation.controllers;

import com.air.navigation.mail.MailService;
import com.air.navigation.security.services.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/mail")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAuthority('ADMIN')")
@Tag(name = "Administration", description = "Diagnostic de la messagerie e-mail")
public class AdminMailController {

    private final MailService mailService;

    @GetMapping("/status")
    @Operation(summary = "État de la configuration SMTP")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(mailService.status());
    }

    /** Envoie un e-mail de test (à l'adresse donnée, sinon à l'admin connecté) et renvoie le diagnostic */
    @PostMapping("/test")
    @Operation(summary = "Envoyer un e-mail de test")
    public ResponseEntity<Map<String, Object>> test(@RequestBody(required = false) Map<String, String> body,
                                                    @AuthenticationPrincipal UserDetailsImpl currentUser) {
        String to = body != null && body.get("to") != null && !body.get("to").isBlank()
                ? body.get("to").trim() : currentUser.getUsername();
        if (!to.matches("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")) {
            throw new IllegalArgumentException("Adresse e-mail invalide : " + to);
        }
        return ResponseEntity.ok(mailService.sendTest(to));
    }
}
