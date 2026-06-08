package com.air.navigation.controllers;

import com.air.navigation.dto.response.ActivityDto;
import com.air.navigation.entities.Flight;
import com.air.navigation.entities.User;
import com.air.navigation.repository.FlightRepository;
import com.air.navigation.repository.UserRepository;
import com.air.navigation.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ══════════════════════════════════════════════════════════════
 * ACTIVITY CONTROLLER — Journal dynamique des événements
 * ══════════════════════════════════════════════════════════════
 *
 * Agrège en temps réel les actions récentes depuis :
 *  ► Table users    → créations, désactivations
 *  ► Table flights  → créations, mises à jour de statut
 *  ► Table chat_messages → messages récents
 *
 * Aucune table d'audit dédiée requise : tout est calculé à la volée
 * à partir des timestamps Hibernate (@CreationTimestamp / @UpdateTimestamp).
 */
@RestController
@RequestMapping("/api/activities")
@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
@Slf4j
public class ActivityController {

    private final UserRepository       userRepository;
    private final FlightRepository     flightRepository;
    private final ChatMessageRepository chatMessageRepository;

    /**
     * GET /api/activities
     *
     * @param limit  Nombre maximum d'activités (défaut 50)
     * @return Liste triée par timestamp décroissant (la plus récente en premier)
     */
    @GetMapping
    public ResponseEntity<List<ActivityDto>> getActivities(
            @RequestParam(defaultValue = "50") int limit) {

        log.debug("GET /api/activities?limit={}", limit);

        List<ActivityDto> activities = new ArrayList<>();
        AtomicLong idGen = new AtomicLong(1);

        // ── 1. UTILISATEURS ──────────────────────────────────────────────
        try {
            List<User> users = userRepository.findAll();
            for (User user : users) {

                // Création d'un utilisateur
                if (user.getCreatedAt() != null) {
                    String roles = user.getRoles().stream()
                            .map(r -> r.getName().name().replace("ROLE_", ""))
                            .findFirst().orElse("Utilisateur");

                    activities.add(ActivityDto.builder()
                            .id(idGen.getAndIncrement())
                            .title("Nouvel utilisateur inscrit")
                            .description(String.format(
                                    "L'utilisateur %s (%s) a rejoint la plateforme avec le rôle %s.",
                                    user.getFullName(), user.getEmail(), roles))
                            .type("success")
                            .category("user")
                            .actor("Système")
                            .timestamp(user.getCreatedAt())
                            .build());
                }

                // Désactivation (updatedAt > createdAt + 1s et isActive = false)
                if (!user.isActive() && user.getUpdatedAt() != null
                        && user.getCreatedAt() != null
                        && user.getUpdatedAt().isAfter(user.getCreatedAt().plusSeconds(1))) {
                    activities.add(ActivityDto.builder()
                            .id(idGen.getAndIncrement())
                            .title("Compte utilisateur désactivé")
                            .description(String.format(
                                    "Le compte de %s (%s) a été suspendu.",
                                    user.getFullName(), user.getEmail()))
                            .type("warning")
                            .category("user")
                            .actor("Administrateur")
                            .timestamp(user.getUpdatedAt())
                            .build());
                }
            }
        } catch (Exception e) {
            log.warn("Erreur lors de la récupération des utilisateurs : {}", e.getMessage());
        }

        // ── 2. VOLS ───────────────────────────────────────────────────────
        try {
            List<Flight> flights = flightRepository.findAll();
            for (Flight flight : flights) {

                // Création du vol
                if (flight.getCreatedAt() != null) {
                    activities.add(ActivityDto.builder()
                            .id(idGen.getAndIncrement())
                            .title("Vol " + flight.getFlightNumber() + " créé")
                            .description(String.format(
                                    "Vol %s → %s (%s) programmé le %s.",
                                    flight.getOrigin(), flight.getDestination(),
                                    flight.getAirline(),
                                    flight.getScheduledTime() != null
                                            ? flight.getScheduledTime().toString().replace("T", " à ").substring(0, Math.min(22, flight.getScheduledTime().toString().length()))
                                            : "—"))
                            .type("success")
                            .category("flight")
                            .actor("Système")
                            .timestamp(flight.getCreatedAt())
                            .build());
                }

                // Mise à jour du statut (updatedAt différent de createdAt)
                if (flight.getUpdatedAt() != null && flight.getCreatedAt() != null
                        && flight.getUpdatedAt().isAfter(flight.getCreatedAt().plusSeconds(5))) {
                    String statusLabel = mapFlightStatus(flight.getStatus().name());
                    String type = flight.getStatus().name().equals("RETARDE") ? "warning"
                            : flight.getStatus().name().equals("ANNULE") ? "error"
                            : "info";

                    activities.add(ActivityDto.builder()
                            .id(idGen.getAndIncrement())
                            .title("Statut vol " + flight.getFlightNumber() + " mis à jour")
                            .description(String.format(
                                    "Le vol %s (%s → %s) est maintenant : %s.",
                                    flight.getFlightNumber(), flight.getOrigin(),
                                    flight.getDestination(), statusLabel))
                            .type(type)
                            .category("flight")
                            .actor("Agent ATC")
                            .timestamp(flight.getUpdatedAt())
                            .build());
                }
            }
        } catch (Exception e) {
            log.warn("Erreur lors de la récupération des vols : {}", e.getMessage());
        }

        // ── 3. MESSAGES CHAT (résumé) ─────────────────────────────────────
        try {
            var pageable = PageRequest.of(0, 20);
            var messages = chatMessageRepository.findAll(pageable).getContent();
            for (var msg : messages) {
                if (msg.getSentAt() != null) {
                    activities.add(ActivityDto.builder()
                            .id(idGen.getAndIncrement())
                            .title("Message dans le salon « " + (msg.getChatRoom() != null ? msg.getChatRoom().getName() : "Chat") + " »")
                            .description(String.format(
                                    "%s a envoyé un message : « %s »",
                                    msg.getSender() != null ? msg.getSender().getFullName() : "Utilisateur",
                                    truncate(msg.getContent(), 80)))
                            .type("info")
                            .category("chat")
                            .actor(msg.getSender() != null ? msg.getSender().getFullName() : "Utilisateur")
                            .timestamp(msg.getSentAt())
                            .build());
                }
            }
        } catch (Exception e) {
            log.warn("Erreur lors de la récupération des messages chat : {}", e.getMessage());
        }

        // ── Ajout d'un événement système de démarrage ─────────────────────
        activities.add(ActivityDto.builder()
                .id(idGen.getAndIncrement())
                .title("Plateforme SkyGuide démarrée")
                .description("Le serveur de navigation aérienne est opérationnel et accessible.")
                .type("success")
                .category("system")
                .actor("Système")
                .timestamp(LocalDateTime.now().minusHours(1))
                .build());

        // ── Tri par date décroissante + limite ────────────────────────────
        List<ActivityDto> sorted = activities.stream()
                .filter(a -> a.getTimestamp() != null)
                .sorted(Comparator.comparing(ActivityDto::getTimestamp).reversed())
                .limit(limit)
                .toList();

        log.debug("Retour de {} activités agrégées", sorted.size());
        return ResponseEntity.ok(sorted);
    }

    /**
     * GET /api/activities/stats
     * Retourne les compteurs globaux pour les KPIs.
     */
    @GetMapping("/stats")
    public ResponseEntity<java.util.Map<String, Long>> getStats() {
        long totalUsers   = userRepository.count();
        long totalFlights = flightRepository.count();
        long totalChats   = chatMessageRepository.count();

        return ResponseEntity.ok(java.util.Map.of(
                "totalUsers",   totalUsers,
                "totalFlights", totalFlights,
                "totalChats",   totalChats
        ));
    }

    // ── Utilitaires ───────────────────────────────────────────────────────

    private String mapFlightStatus(String status) {
        return switch (status) {
            case "PROGRAMME"       -> "Programmé";
            case "EN_EMBARQUEMENT" -> "En Embarquement";
            case "EN_VOL"          -> "En Vol";
            case "EN_APPROCHE"     -> "En Approche";
            case "ATTERRI"         -> "Atterri";
            case "AU_SOL"          -> "Au Sol";
            case "RETARDE"         -> "Retardé";
            case "ANNULE"          -> "Annulé";
            case "DIVERTE"         -> "Dérouté";
            default                -> status;
        };
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "…";
    }
}
