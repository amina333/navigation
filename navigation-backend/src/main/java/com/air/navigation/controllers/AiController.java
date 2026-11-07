package com.air.navigation.controllers;

import com.air.navigation.dto.request.AiChatRequestDto;
import com.air.navigation.dto.response.AiChatResponseDto;
import com.air.navigation.security.services.UserDetailsImpl;
import com.air.navigation.services.ai.SkyAiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * SkyAI — Assistant IA métier (§8.8 / §16).
 * La logique est dans {@link SkyAiService} ; chaque échange est journalisé.
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "SkyAI", description = "Assistant métier — sources citées, traçable, sans décision critique")
public class AiController {

    private final SkyAiService skyAiService;

    @PostMapping("/chat")
    @Operation(summary = "Poser une question à SkyAI")
    public ResponseEntity<AiChatResponseDto> chat(@Valid @RequestBody AiChatRequestDto request,
                                                  @AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(skyAiService.ask(currentUser.getId(), request.getMessage()));
    }

    @GetMapping("/history")
    @Operation(summary = "Historique de mes échanges avec SkyAI")
    public ResponseEntity<List<AiChatResponseDto>> history(@RequestParam(defaultValue = "30") int limit,
                                                           @AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(skyAiService.history(currentUser.getId(), limit));
    }

    @DeleteMapping("/history")
    @Operation(summary = "Effacer mon historique (conservé pour l'audit)")
    public ResponseEntity<Void> clearHistory(@AuthenticationPrincipal UserDetailsImpl currentUser) {
        skyAiService.clearHistory(currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/status")
    @Operation(summary = "Mode de fonctionnement et suggestions adaptées au rôle")
    public ResponseEntity<Map<String, Object>> status(@AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(skyAiService.status(currentUser.getId()));
    }
}
