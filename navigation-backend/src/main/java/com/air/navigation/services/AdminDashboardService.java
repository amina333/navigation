package com.air.navigation.services;

import com.air.navigation.entities.ChatMessage;
import com.air.navigation.entities.Flight;
import com.air.navigation.entities.User;
import com.air.navigation.enums.ERole;
import com.air.navigation.enums.FlightStatus;
import com.air.navigation.enums.MessageType;
import com.air.navigation.mail.MailService;
import com.air.navigation.repository.*;
import com.air.navigation.services.ai.SkyAiService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.management.ManagementFactory;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Données du tableau de bord administrateur, calculées en direct sur une période
 * (aujourd'hui, 7 jours, 30 jours) et comparées à la période précédente de même durée.
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private static final EnumSet<FlightStatus> ACTIVE = EnumSet.of(
            FlightStatus.EN_EMBARQUEMENT, FlightStatus.DECOLLAGE, FlightStatus.EN_VOL,
            FlightStatus.EN_APPROCHE, FlightStatus.AU_SOL);

    private final UserRepository userRepository;
    private final FlightRepository flightRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final AiConversationRepository aiConversationRepository;
    private final MailService mailService;
    private final MinioService minioService;
    private final SkyAiService skyAiService;
    private final SimpUserRegistry simpUserRegistry;

    @Transactional(readOnly = true)
    public Map<String, Object> compute(String period) {
        String p = switch (period == null ? "" : period) {
            case "week", "month" -> period;
            default -> "today";
        };
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = switch (p) {
            case "week" -> LocalDate.now().minusDays(6).atStartOfDay();
            case "month" -> LocalDate.now().minusDays(29).atStartOfDay();
            default -> LocalDate.now().atStartOfDay();
        };
        LocalDateTime prevFrom = from.minus(java.time.Duration.between(from, p.equals("today") ? from.plusDays(1) : now.plusSeconds(1)));
        LocalDateTime prevTo = from;

        List<User> users = userRepository.findAll();
        List<Flight> flights = flightRepository.findAll();
        List<ChatMessage> messages = chatMessageRepository.findAll().stream()
                .filter(m -> m.getMessageType() != MessageType.SYSTEM).toList();
        var ai = aiConversationRepository.findAll();

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("period", p);
        res.put("from", from.toString());
        res.put("generatedAt", now.withNano(0).toString());

        // ── Utilisateurs ──
        long newUsers = users.stream().filter(u -> in(u.getCreatedAt(), from, now)).count();
        long newUsersPrev = users.stream().filter(u -> in(u.getCreatedAt(), prevFrom, prevTo)).count();
        res.put("users", map(
                "total", users.size(),
                "active", users.stream().filter(User::isActive).count(),
                "newInPeriod", newUsers,
                "newPrev", newUsersPrev,
                "trendPct", trend(newUsers, newUsersPrev)));

        // ── Vols ──
        List<Flight> inPeriod = flights.stream().filter(f -> in(f.getScheduledTime(), from, endOf(p, from, now))).toList();
        long prevFlights = flights.stream().filter(f -> in(f.getScheduledTime(), prevFrom, prevTo)).count();
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (FlightStatus s : FlightStatus.values()) byStatus.put(s.name(), 0L);
        inPeriod.forEach(f -> { if (f.getStatus() != null) byStatus.merge(f.getStatus().name(), 1L, Long::sum); });
        res.put("flights", map(
                "total", flights.size(),
                "activeNow", flights.stream().filter(f -> f.getStatus() != null && ACTIVE.contains(f.getStatus())).count(),
                "inPeriod", inPeriod.size(),
                "prevPeriod", prevFlights,
                "trendPct", trend(inPeriod.size(), prevFlights),
                "byStatus", byStatus));

        // ── Messagerie & SkyAI ──
        long msgs = messages.stream().filter(m -> in(m.getSentAt(), from, now)).count();
        long msgsPrev = messages.stream().filter(m -> in(m.getSentAt(), prevFrom, prevTo)).count();
        res.put("messages", map("inPeriod", msgs, "prevPeriod", msgsPrev, "trendPct", trend(msgs, msgsPrev)));
        res.put("aiQueries", map("inPeriod", ai.stream().filter(c -> in(c.getCreatedAt(), from, now)).count()));

        // ── Rôles (tous les rôles du cahier des charges) ──
        Map<String, Long> roles = new LinkedHashMap<>();
        for (ERole r : ERole.values()) roles.put(r.name(), 0L);
        users.forEach(u -> u.getRoles().forEach(r -> roles.merge(r.getName().name(), 1L, Long::sum)));
        res.put("roles", roles);

        // ── État du système (vérifications réelles) ──
        List<Map<String, Object>> checks = new ArrayList<>();
        boolean dbOk;
        try { userRepository.count(); dbOk = true; } catch (Exception e) { dbOk = false; }
        checks.add(check("database", "Base de données", dbOk, dbOk ? "PostgreSQL connectée" : "Indisponible"));
        int wsUsers = simpUserRegistry.getUserCount();
        checks.add(check("websocket", "Temps réel (WebSocket)", true, wsUsers + " agent(s) connecté(s)"));
        boolean smtp = mailService.isSmtpConfigured();
        checks.add(check("mail", "Envoi d'e-mails", smtp, smtp ? "SMTP actif" : "Non configuré (outbox locale)"));
        boolean minio = minioService.isMinioAvailable();
        checks.add(check("storage", "Stockage fichiers", true, minio ? "MinIO" : "Stockage local (secours)"));
        checks.add(check("ai", "Assistant SkyAI", true, skyAiService.isLlmEnabled() ? "IA générative" : "Moteur métier"));
        long up = checks.stream().filter(c -> Boolean.TRUE.equals(c.get("ok"))).count();
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
        res.put("system", map(
                "servicesUp", up,
                "servicesTotal", checks.size(),
                "availabilityPct", Math.round(1000.0 * up / checks.size()) / 10.0,
                "status", up == checks.size() ? "OK" : (dbOk ? "DEGRADED" : "DOWN"),
                "uptimeSeconds", uptime,
                "connectedUsers", wsUsers,
                "checks", checks));
        return res;
    }

    private static LocalDateTime endOf(String period, LocalDateTime from, LocalDateTime now) {
        // Pour les vols, « aujourd'hui » couvre toute la journée (vols prévus plus tard inclus)
        return period.equals("today") ? from.plusDays(1).minusNanos(1) : now.plusDays(1);
    }

    private static boolean in(LocalDateTime t, LocalDateTime from, LocalDateTime to) {
        return t != null && !t.isBefore(from) && !t.isAfter(to);
    }

    /** Variation en % par rapport à la période précédente ; null si la période précédente est vide */
    private static Double trend(long now, long before) {
        if (before == 0) return now == 0 ? 0.0 : null;
        return Math.round(1000.0 * (now - before) / before) / 10.0;
    }

    /** Map ordonnée acceptant les valeurs null (contrairement à Map.of) : clé, valeur, clé, valeur… */
    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static Map<String, Object> check(String key, String label, boolean ok, String detail) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        m.put("ok", ok);
        m.put("detail", detail);
        return m;
    }
}
