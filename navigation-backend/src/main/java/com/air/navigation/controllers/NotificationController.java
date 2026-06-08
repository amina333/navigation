package com.air.navigation.controllers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Contrôleur REST pour les notifications.
 * Retourne une liste vide pour l'instant — les notifications seront
 * enrichies via WebSocket et événements métier en Phase 2.
 */
@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class NotificationController {

    /**
     * GET /api/notifications
     * Retourne toutes les notifications de l'utilisateur courant.
     * Phase 1 : liste vide — évite le 500 côté frontend.
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getNotifications() {
        log.debug("GET /api/notifications — retour liste vide (Phase 1)");
        return ResponseEntity.ok(List.of());
    }

    /**
     * GET /api/notifications/unread/count
     * Retourne le nombre de notifications non lues.
     */
    @GetMapping("/unread/count")
    public ResponseEntity<Map<String, Integer>> getUnreadCount() {
        return ResponseEntity.ok(Map.of("count", 0));
    }

    /**
     * PATCH /api/notifications/{id}/read
     * Marque une notification comme lue.
     */
    @PatchMapping("/{id}/read")
    public ResponseEntity<Map<String, Object>> markAsRead(@PathVariable Long id) {
        log.debug("PATCH /api/notifications/{}/read", id);
        return ResponseEntity.ok(Map.of(
            "id", id,
            "read", true,
            "readAt", LocalDateTime.now().toString()
        ));
    }

    /**
     * PATCH /api/notifications/read-all
     * Marque toutes les notifications comme lues.
     */
    @PatchMapping("/read-all")
    public ResponseEntity<Map<String, String>> markAllAsRead() {
        return ResponseEntity.ok(Map.of("message", "Toutes les notifications ont été marquées comme lues."));
    }

    /**
     * DELETE /api/notifications/{id}
     * Supprime une notification.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotification(@PathVariable Long id) {
        log.debug("DELETE /api/notifications/{}", id);
        return ResponseEntity.noContent().build();
    }
}
