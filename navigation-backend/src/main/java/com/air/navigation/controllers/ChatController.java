package com.air.navigation.controllers;

import com.air.navigation.dto.request.ChatMessageRequestDto;
import com.air.navigation.dto.request.ChatRoomRequestDto;
import com.air.navigation.dto.response.ChatMessageResponseDto;
import com.air.navigation.dto.response.ChatRoomResponseDto;
import com.air.navigation.security.services.UserDetailsImpl;
import com.air.navigation.services.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Chat", description = "Messagerie interne §8.7 — temps réel, sécurisée, traçable")
public class ChatController {

    private final ChatService chatService;

    // ══════════════════════════════════════════════════════
    // SALONS
    // ══════════════════════════════════════════════════════

    @GetMapping("/rooms/public")
    @Operation(summary = "Salons publics")
    public ResponseEntity<List<ChatRoomResponseDto>> getPublicRooms() {
        return ResponseEntity.ok(chatService.getAllPublicRooms());
    }

    @GetMapping("/rooms/my")
    @Operation(summary = "Mes salons")
    public ResponseEntity<List<ChatRoomResponseDto>> getMyRooms(
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(chatService.getRoomsForUser(currentUser.getId()));
    }

    @PostMapping("/rooms")
    @Operation(summary = "Créer un salon de groupe")
    public ResponseEntity<ChatRoomResponseDto> createRoom(
            @Valid @RequestBody ChatRoomRequestDto dto,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatService.createRoom(dto, currentUser.getId()));
    }

    @PatchMapping("/rooms/{roomId}/members/{userId}")
    @Operation(summary = "Ajouter un membre à un salon")
    public ResponseEntity<ChatRoomResponseDto> addMember(@PathVariable Long roomId,
                                                         @PathVariable Long userId) {
        return ResponseEntity.ok(chatService.addMemberToRoom(roomId, userId));
    }

    /** Conversation privée (DM) entre l'utilisateur courant et targetUserId */
    @PostMapping("/dm/{targetUserId}")
    @Operation(summary = "Obtenir/créer une conversation privée")
    public ResponseEntity<ChatRoomResponseDto> getOrCreateDm(
            @PathVariable Long targetUserId,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(chatService.getOrCreateDm(currentUser.getId(), targetUserId));
    }

    /** Salon officiel par service (ATC, PISTE, AIS…) */
    @PostMapping("/service/{serviceCode}")
    @Operation(summary = "Obtenir/créer un salon de service")
    public ResponseEntity<ChatRoomResponseDto> getOrCreateServiceChannel(
            @PathVariable String serviceCode,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(chatService.getOrCreateServiceChannel(serviceCode, currentUser.getId()));
    }

    // ══════════════════════════════════════════════════════
    // MESSAGES
    // ══════════════════════════════════════════════════════

    @GetMapping("/rooms/{roomId}/messages")
    @Operation(summary = "Historique des messages")
    public ResponseEntity<List<ChatMessageResponseDto>> getRoomHistory(@PathVariable Long roomId) {
        return ResponseEntity.ok(chatService.getRoomHistory(roomId));
    }

    @GetMapping("/rooms/{roomId}/search")
    @Operation(summary = "Recherche dans l'historique §8.7")
    public ResponseEntity<List<ChatMessageResponseDto>> searchMessages(
            @PathVariable Long roomId,
            @RequestParam String q) {
        return ResponseEntity.ok(chatService.searchMessages(roomId, q));
    }

    @PostMapping("/messages")
    @Operation(summary = "Envoyer un message texte (REST)")
    public ResponseEntity<ChatMessageResponseDto> sendMessage(
            @Valid @RequestBody ChatMessageRequestDto dto,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatService.sendMessage(dto, currentUser.getId()));
    }

    // ══════════════════════════════════════════════════════
    // PIÈCES JOINTES (MinIO)
    // ══════════════════════════════════════════════════════

    @PostMapping(value = "/rooms/{roomId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Envoyer un fichier (MinIO)")
    public ResponseEntity<ChatMessageResponseDto> uploadFile(
            @PathVariable Long roomId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatService.uploadAttachment(roomId, file, currentUser.getId()));
    }

    // ══════════════════════════════════════════════════════
    // MESSAGES ÉPINGLÉS §8.7
    // ══════════════════════════════════════════════════════

    @GetMapping("/rooms/{roomId}/pinned")
    @Operation(summary = "Messages épinglés du salon")
    public ResponseEntity<List<ChatMessageResponseDto>> getPinnedMessages(@PathVariable Long roomId) {
        return ResponseEntity.ok(chatService.getPinnedMessages(roomId));
    }

    @PostMapping("/messages/{messageId}/pin")
    @Operation(summary = "Épingler un message")
    public ResponseEntity<ChatMessageResponseDto> pinMessage(
            @PathVariable Long messageId,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(chatService.pinMessage(messageId, currentUser.getId()));
    }

    @DeleteMapping("/messages/{messageId}/pin")
    @Operation(summary = "Désépingler un message")
    public ResponseEntity<ChatMessageResponseDto> unpinMessage(
            @PathVariable Long messageId,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(chatService.unpinMessage(messageId, currentUser.getId()));
    }
}
