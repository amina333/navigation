package com.air.navigation.controllers;

import com.air.navigation.dto.request.ChatMessageRequestDto;
import com.air.navigation.dto.request.ChatRoomRequestDto;
import com.air.navigation.dto.response.ChatContactDto;
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
import java.util.Map;

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
    public ResponseEntity<List<ChatRoomResponseDto>> getPublicRooms(
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(chatService.getAllPublicRooms(currentUser.getId()));
    }

    @GetMapping("/rooms/my")
    @Operation(summary = "Toutes les conversations accessibles (publics, services, DM, groupes)")
    public ResponseEntity<List<ChatRoomResponseDto>> getMyRooms(
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(chatService.getRoomsForUser(currentUser.getId()));
    }

    @GetMapping("/rooms/{roomId}")
    @Operation(summary = "Détail d'un salon")
    public ResponseEntity<ChatRoomResponseDto> getRoom(@PathVariable Long roomId,
                                                       @AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(chatService.getRoom(roomId, currentUser.getId()));
    }

    @PostMapping("/rooms")
    @Operation(summary = "Créer un groupe privé ou un salon public")
    public ResponseEntity<ChatRoomResponseDto> createRoom(
            @Valid @RequestBody ChatRoomRequestDto dto,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatService.createRoom(dto, currentUser.getId()));
    }

    @PatchMapping("/rooms/{roomId}/members/{userId}")
    @Operation(summary = "Ajouter un membre à un groupe")
    public ResponseEntity<ChatRoomResponseDto> addMember(@PathVariable Long roomId,
                                                         @PathVariable Long userId,
                                                         @AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(chatService.addMemberToRoom(roomId, userId, currentUser.getId()));
    }

    @DeleteMapping("/rooms/{roomId}/members/me")
    @Operation(summary = "Quitter un groupe")
    public ResponseEntity<Void> leaveRoom(@PathVariable Long roomId,
                                          @AuthenticationPrincipal UserDetailsImpl currentUser) {
        chatService.leaveRoom(roomId, currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/rooms/{roomId}/read")
    @Operation(summary = "Marquer le salon comme lu")
    public ResponseEntity<Void> markAsRead(@PathVariable Long roomId,
                                           @AuthenticationPrincipal UserDetailsImpl currentUser) {
        chatService.markRoomAsRead(roomId, currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Nombre total de messages non lus")
    public ResponseEntity<Map<String, Long>> unreadCount(@AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(Map.of("count", chatService.getTotalUnread(currentUser.getId())));
    }

    /** Annuaire pour démarrer une conversation privée / créer un groupe */
    @GetMapping("/contacts")
    @Operation(summary = "Annuaire des agents actifs")
    public ResponseEntity<List<ChatContactDto>> contacts(@AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(chatService.getContacts(currentUser.getId()));
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
    public ResponseEntity<List<ChatMessageResponseDto>> getRoomHistory(
            @PathVariable Long roomId,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(chatService.getRoomHistory(roomId, currentUser.getId()));
    }

    @GetMapping("/rooms/{roomId}/search")
    @Operation(summary = "Recherche dans l'historique §8.7")
    public ResponseEntity<List<ChatMessageResponseDto>> searchMessages(
            @PathVariable Long roomId,
            @RequestParam String q,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(chatService.searchMessages(roomId, q, currentUser.getId()));
    }

    @PostMapping("/messages")
    @Operation(summary = "Envoyer un message texte (REST)")
    public ResponseEntity<ChatMessageResponseDto> sendMessage(
            @Valid @RequestBody ChatMessageRequestDto dto,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatService.sendMessage(dto, currentUser.getId()));
    }

    @DeleteMapping("/messages/{messageId}")
    @Operation(summary = "Supprimer (logiquement) un message")
    public ResponseEntity<ChatMessageResponseDto> deleteMessage(
            @PathVariable Long messageId,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(chatService.deleteMessage(messageId, currentUser.getId()));
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
    public ResponseEntity<List<ChatMessageResponseDto>> getPinnedMessages(
            @PathVariable Long roomId,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(chatService.getPinnedMessages(roomId, currentUser.getId()));
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
