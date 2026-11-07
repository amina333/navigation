package com.air.navigation.services;

import com.air.navigation.entities.*;
import com.air.navigation.enums.FlightStatus;
import com.air.navigation.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Statistiques globales pour le tableau de bord d'administration (§8.4 / §9.3).
 * Agrégation en mémoire : adapté aux volumes de la plateforme ; à basculer en requêtes SQL agrégées si besoin.
 */
@Service
@RequiredArgsConstructor
public class AdminStatisticsService {

    private static final int DAYS = 14;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final UserRepository userRepository;
    private final FlightRepository flightRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final AiConversationRepository aiConversationRepository;

    public record Point(String label, long value) {}

    @Transactional(readOnly = true)
    public Map<String, Object> compute() {
        List<User> users = userRepository.findAll();
        List<Flight> flights = flightRepository.findAll();
        List<ChatRoom> rooms = chatRoomRepository.findAll();
        List<ChatMessage> messages = chatMessageRepository.findAll();
        List<AiConversation> ai = aiConversationRepository.findAll();

        LocalDate today = LocalDate.now();
        LocalDateTime since7 = today.minusDays(6).atStartOfDay();

        long flightsToday = flights.stream().filter(f -> f.getScheduledTime() != null && f.getScheduledTime().toLocalDate().equals(today)).count();
        long delayed = flights.stream().filter(f -> f.getStatus() == FlightStatus.RETARDE).count();
        long cancelled = flights.stream().filter(f -> f.getStatus() == FlightStatus.ANNULE).count();
        List<ChatMessage> realMessages = messages.stream()
                .filter(m -> m.getMessageType() != com.air.navigation.enums.MessageType.SYSTEM).toList();

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("users", users.size());
        kpis.put("activeUsers", users.stream().filter(User::isActive).count());
        kpis.put("newUsers30d", users.stream().filter(u -> u.getCreatedAt() != null && u.getCreatedAt().isAfter(LocalDateTime.now().minusDays(30))).count());
        kpis.put("flights", flights.size());
        kpis.put("flightsToday", flightsToday);
        kpis.put("delayRate", pct(delayed, flights.size()));
        kpis.put("cancelRate", pct(cancelled, flights.size()));
        kpis.put("onTimeRate", pct(flights.size() - delayed - cancelled, flights.size()));
        kpis.put("rooms", rooms.size());
        kpis.put("messages", realMessages.size());
        kpis.put("messages7d", realMessages.stream().filter(m -> m.getSentAt() != null && m.getSentAt().isAfter(since7)).count());
        kpis.put("attachments", realMessages.stream().filter(m -> m.getFileUrl() != null).count());
        kpis.put("aiQueries", ai.size());
        kpis.put("aiQueries7d", ai.stream().filter(c -> c.getCreatedAt() != null && c.getCreatedAt().isAfter(since7)).count());

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("generatedAt", LocalDateTime.now().withNano(0).toString());
        res.put("kpis", kpis);

        // ── Utilisateurs ──
        res.put("usersByRole", count(users.stream().flatMap(u -> u.getRoles().stream().map(r -> r.getName().name())).toList(), Function.identity(), false));
        res.put("usersStatus", List.of(
                new Point("Actifs", users.stream().filter(User::isActive).count()),
                new Point("Désactivés", users.stream().filter(u -> !u.isActive()).count())));
        List<Point> monthly = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth ym = YearMonth.now().minusMonths(i);
            long n = users.stream().filter(u -> u.getCreatedAt() != null && YearMonth.from(u.getCreatedAt()).equals(ym)).count();
            monthly.add(new Point(ym.toString(), n));
        }
        res.put("usersCreatedMonthly", monthly);
        res.put("recentUsers", users.stream()
                .filter(u -> u.getCreatedAt() != null)
                .sorted(Comparator.comparing(User::getCreatedAt).reversed())
                .limit(6)
                .map(u -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", u.getId());
                    m.put("fullName", u.getFullName());
                    m.put("email", u.getEmail());
                    m.put("roles", u.getRoles().stream().map(r -> r.getName().name()).sorted().toList());
                    m.put("active", u.isActive());
                    m.put("createdAt", u.getCreatedAt().withNano(0).toString());
                    return m;
                }).toList());

        // ── Vols ──
        res.put("flightsByStatus", count(flights, f -> f.getStatus() == null ? "INCONNU" : f.getStatus().name(), false));
        res.put("flightsByType", count(flights, f -> f.getFlightType() == null ? "INCONNU" : f.getFlightType().name(), false));
        res.put("flightsByAirline", topN(count(flights, Flight::getAirline, true), 6));
        res.put("flightsByAircraft", topN(count(flights, Flight::getAircraftType, true), 6));
        long[] hours = new long[24];
        flights.stream().filter(f -> f.getScheduledTime() != null).forEach(f -> hours[f.getScheduledTime().getHour()]++);
        List<Point> byHour = new ArrayList<>();
        for (int h = 0; h < 24; h++) byHour.add(new Point(String.format("%02dh", h), hours[h]));
        res.put("flightsByHour", byHour);

        List<Map<String, Object>> daily = new ArrayList<>();
        for (int i = DAYS - 1; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            List<Flight> fd = flights.stream().filter(f -> f.getScheduledTime() != null && f.getScheduledTime().toLocalDate().equals(d)).toList();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", d.format(DAY));
            m.put("total", fd.size());
            m.put("delayed", fd.stream().filter(f -> f.getStatus() == FlightStatus.RETARDE).count());
            m.put("cancelled", fd.stream().filter(f -> f.getStatus() == FlightStatus.ANNULE).count());
            daily.add(m);
        }
        res.put("flightsDaily", daily);

        // ── Communications ──
        res.put("roomsByType", count(rooms, r -> r.getRoomType().name(), false));
        res.put("messagesDaily", dailyCounts(realMessages.stream().map(ChatMessage::getSentAt).toList(), today));
        Map<Long, Long> perRoom = realMessages.stream().collect(Collectors.groupingBy(m -> m.getChatRoom().getId(), Collectors.counting()));
        res.put("topRooms", rooms.stream()
                .filter(r -> r.getRoomType() != com.air.navigation.enums.RoomType.PRIVATE_DM) // confidentialité des DM
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", r.getName());
                    m.put("type", r.getRoomType().name());
                    m.put("messages", perRoom.getOrDefault(r.getId(), 0L));
                    return m;
                })
                .sorted((a, b) -> Long.compare((Long) b.get("messages"), (Long) a.get("messages")))
                .limit(5).toList());

        // ── SkyAI ──
        res.put("aiByIntent", topN(count(ai, c -> c.getIntent() == null ? "UNKNOWN" : c.getIntent(), true), 8));
        res.put("aiByMode", count(ai, AiConversation::getMode, false));
        res.put("aiDaily", dailyCounts(ai.stream().map(AiConversation::getCreatedAt).toList(), today));

        return res;
    }

    private <T> List<Point> count(Collection<T> items, Function<T, String> key, boolean sortByValue) {
        Map<String, Long> m = items.stream().map(key).filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.groupingBy(Function.identity(), TreeMap::new, Collectors.counting()));
        List<Point> list = m.entrySet().stream().map(e -> new Point(e.getKey(), e.getValue())).collect(Collectors.toList());
        if (sortByValue) list.sort(Comparator.comparingLong(Point::value).reversed());
        return list;
    }

    private List<Point> topN(List<Point> sorted, int n) {
        if (sorted.size() <= n) return sorted;
        List<Point> out = new ArrayList<>(sorted.subList(0, n));
        long rest = sorted.subList(n, sorted.size()).stream().mapToLong(Point::value).sum();
        out.add(new Point("Autres", rest));
        return out;
    }

    private List<Point> dailyCounts(List<LocalDateTime> stamps, LocalDate today) {
        List<Point> out = new ArrayList<>();
        for (int i = DAYS - 1; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            long n = stamps.stream().filter(t -> t != null && t.toLocalDate().equals(d)).count();
            out.add(new Point(d.format(DAY), n));
        }
        return out;
    }

    private double pct(long part, long total) {
        return total == 0 ? 0 : Math.round(1000.0 * part / total) / 10.0;
    }
}
