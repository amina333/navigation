package com.air.navigation.services.ai;

import com.air.navigation.entities.Flight;
import com.air.navigation.entities.User;
import com.air.navigation.enums.FlightStatus;
import com.air.navigation.enums.FlightType;
import com.air.navigation.repository.FlightRepository;
import com.air.navigation.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Collecte les données réelles de la plateforme pour SkyAI (contexte RAG « léger »).
 * Toutes les réponses chiffrées de l'assistant proviennent d'ici, avec leur source.
 */
@Service
@RequiredArgsConstructor
public class AiContextService {

    public static final String SRC_FLIGHTS = "Base des vols de la plateforme";
    public static final String SRC_USERS = "Annuaire des agents (plateforme)";

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Pattern FLIGHT_NO = Pattern.compile("\\b([A-Z]{2,3})\\s?-?(\\d{1,4}[A-Z]?)\\b");
    private static final int DELAY_THRESHOLD_MIN = 15;

    private final FlightRepository flightRepository;
    private final UserRepository userRepository;

    /** Photographie du trafic d'une journée */
    public record FlightSnapshot(
            LocalDate date,
            List<Flight> flights,
            Map<FlightStatus, Long> byStatus,
            long arrivals,
            long departures,
            List<Flight> delayed,
            List<Flight> cancelled,
            List<Flight> active,
            Map<String, Long> byAirline,
            Map<String, Long> byAircraft,
            Map<Integer, Long> byHour) {

        public int total() { return flights.size(); }

        public double delayRate() { return total() == 0 ? 0 : 100.0 * delayed.size() / total(); }

        public double cancelRate() { return total() == 0 ? 0 : 100.0 * cancelled.size() / total(); }
    }

    /** Anomalie non critique de qualité de données (§8.8 « détection d'anomalies non critiques ») */
    public record Anomaly(String flightNumber, String description) {}

    @Transactional(readOnly = true)
    public FlightSnapshot snapshot(LocalDate date) {
        List<Flight> flights = flightRepository.findFlightsByDateRange(
                date.atStartOfDay(), date.plusDays(1).atStartOfDay().minusNanos(1));

        Map<FlightStatus, Long> byStatus = flights.stream()
                .filter(f -> f.getStatus() != null)
                .collect(Collectors.groupingBy(Flight::getStatus, () -> new EnumMap<>(FlightStatus.class), Collectors.counting()));

        List<Flight> cancelled = flights.stream().filter(f -> f.getStatus() == FlightStatus.ANNULE).toList();
        List<Flight> delayed = flights.stream().filter(this::isDelayed).toList();
        List<Flight> active = flights.stream().filter(f -> f.getStatus() != null && EnumSet.of(
                FlightStatus.EN_EMBARQUEMENT, FlightStatus.DECOLLAGE, FlightStatus.EN_VOL,
                FlightStatus.EN_APPROCHE, FlightStatus.AU_SOL).contains(f.getStatus())).toList();

        return new FlightSnapshot(
                date, flights, byStatus,
                flights.stream().filter(f -> f.getFlightType() == FlightType.ARRIVEE).count(),
                flights.stream().filter(f -> f.getFlightType() == FlightType.DEPART).count(),
                delayed, cancelled, active,
                topCounts(flights, Flight::getAirline),
                topCounts(flights, Flight::getAircraftType),
                flights.stream().filter(f -> f.getScheduledTime() != null)
                        .collect(Collectors.groupingBy(f -> f.getScheduledTime().getHour(), TreeMap::new, Collectors.counting())));
    }

    @Transactional(readOnly = true)
    public FlightSnapshot today() {
        return snapshot(LocalDate.now());
    }

    @Transactional(readOnly = true)
    public long totalFlightsInDatabase() {
        return flightRepository.count();
    }

    public boolean isDelayed(Flight f) {
        if (f.getStatus() == FlightStatus.ANNULE) return false;
        if (f.getStatus() == FlightStatus.RETARDE) return true;
        return delayMinutes(f) >= DELAY_THRESHOLD_MIN;
    }

    public long delayMinutes(Flight f) {
        if (f.getScheduledTime() == null) return 0;
        LocalDateTime ref = f.getActualTime() != null ? f.getActualTime() : f.getEstimatedTime();
        if (ref == null) return 0;
        return Math.max(0, Duration.between(f.getScheduledTime(), ref).toMinutes());
    }

    /** Contrôles de cohérence non critiques sur les données saisies */
    public List<Anomaly> detectAnomalies(List<Flight> flights) {
        LocalDateTime now = LocalDateTime.now();
        List<Anomaly> anomalies = new ArrayList<>();
        for (Flight f : flights) {
            String no = f.getFlightNumber();
            FlightStatus s = f.getStatus();
            if (s == FlightStatus.PROGRAMME && f.getScheduledTime() != null
                    && f.getScheduledTime().isBefore(now.minusMinutes(30))) {
                anomalies.add(new Anomaly(no, "toujours « PROGRAMMÉ » alors que l'heure prévue ("
                        + f.getScheduledTime().format(HM) + ") est dépassée de plus de 30 min — statut à mettre à jour ?"));
            }
            if (s != null && EnumSet.of(FlightStatus.DECOLLAGE, FlightStatus.EN_APPROCHE, FlightStatus.ATTERRI).contains(s)
                    && isBlank(f.getRunway())) {
                anomalies.add(new Anomaly(no, "statut « " + s + " » sans piste renseignée"));
            }
            if (s != null && EnumSet.of(FlightStatus.ATTERRI, FlightStatus.ARRIVE).contains(s) && f.getActualTime() == null) {
                anomalies.add(new Anomaly(no, "marqué « " + s + " » sans heure réelle"));
            }
            if (!isBlank(f.getOriginIata()) && f.getOriginIata().equalsIgnoreCase(f.getDestinationIata())) {
                anomalies.add(new Anomaly(no, "provenance et destination identiques (" + f.getOriginIata() + ")"));
            }
            if (f.getEstimatedTime() != null && f.getScheduledTime() != null
                    && Math.abs(Duration.between(f.getScheduledTime(), f.getEstimatedTime()).toHours()) >= 6) {
                anomalies.add(new Anomaly(no, "écart de plus de 6 h entre heure prévue et estimée — erreur de saisie ?"));
            }
            if (f.getStatus() == FlightStatus.RETARDE && f.getEstimatedTime() == null) {
                anomalies.add(new Anomaly(no, "retardé sans heure estimée communiquée"));
            }
        }
        return anomalies;
    }

    /** Recherche un numéro de vol cité dans la question (ex. « TU 712 », « BJ210 ») */
    @Transactional(readOnly = true)
    public Optional<Flight> findFlightInText(String text) {
        if (text == null) return Optional.empty();
        Matcher m = FLIGHT_NO.matcher(text.toUpperCase());
        while (m.find()) {
            String compact = m.group(1) + m.group(2);
            Optional<Flight> f = flightRepository.findByFlightNumber(compact);
            if (f.isEmpty()) f = flightRepository.findByFlightNumber(m.group(1) + " " + m.group(2));
            if (f.isPresent()) return f;
        }
        return Optional.empty();
    }

    public String describeFlight(Flight f) {
        StringBuilder sb = new StringBuilder();
        sb.append("**").append(f.getFlightNumber()).append("** — ").append(nvl(f.getAirline()))
          .append(f.getFlightType() == FlightType.ARRIVEE ? " (arrivée)" : f.getFlightType() == FlightType.DEPART ? " (départ)" : "").append("\n");
        sb.append("• Trajet : ").append(nvl(f.getOrigin())).append(iata(f.getOriginIata()))
          .append(" → ").append(nvl(f.getDestination())).append(iata(f.getDestinationIata())).append("\n");
        sb.append("• Statut : **").append(f.getStatus()).append("**");
        if (isDelayed(f) && delayMinutes(f) > 0) sb.append(" (≈ ").append(delayMinutes(f)).append(" min de retard)");
        sb.append("\n");
        if (f.getScheduledTime() != null) sb.append("• Heure prévue : ").append(f.getScheduledTime().format(DMY)).append(" ").append(f.getScheduledTime().format(HM)).append("\n");
        if (f.getEstimatedTime() != null) sb.append("• Heure estimée : ").append(f.getEstimatedTime().format(HM)).append("\n");
        if (f.getActualTime() != null) sb.append("• Heure réelle : ").append(f.getActualTime().format(HM)).append("\n");
        if (!isBlank(f.getAircraftType())) sb.append("• Appareil : ").append(f.getAircraftType())
                .append(isBlank(f.getAircraftRegistration()) ? "" : " (" + f.getAircraftRegistration() + ")").append("\n");
        if (!isBlank(f.getRunway())) sb.append("• Piste : ").append(f.getRunway()).append("\n");
        if (!isBlank(f.getGate())) sb.append("• Porte : ").append(f.getGate()).append(isBlank(f.getTerminal()) ? "" : " · Terminal " + f.getTerminal()).append("\n");
        if (!isBlank(f.getRemarks())) sb.append("• Remarques : ").append(f.getRemarks()).append("\n");
        return sb.toString().trim();
    }

    public String shortFlight(Flight f) {
        String time = f.getScheduledTime() != null ? f.getScheduledTime().format(HM) : "--:--";
        String route = nvl(isBlank(f.getOriginIata()) ? f.getOrigin() : f.getOriginIata()) + "→"
                + nvl(isBlank(f.getDestinationIata()) ? f.getDestination() : f.getDestinationIata());
        return f.getFlightNumber() + " " + time + " " + route;
    }

    /** Effectifs par rôle — réservé aux profils autorisés (§8.8 « respect des permissions ») */
    @Transactional(readOnly = true)
    public Map<String, Long> activeUsersByRole() {
        Map<String, Long> res = new TreeMap<>();
        for (User u : userRepository.findAllActiveUsers()) {
            u.getRoles().forEach(r -> res.merge(r.getName().name(), 1L, Long::sum));
        }
        return res;
    }

    /** Bloc de contexte injecté dans le prompt LLM */
    @Transactional(readOnly = true)
    public String buildLlmContext(String question, boolean canSeeUsers) {
        FlightSnapshot s = today();
        StringBuilder sb = new StringBuilder();
        sb.append("[SOURCE: ").append(SRC_FLIGHTS).append(" — extrait du ")
          .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))).append("]\n");
        sb.append("Vols du jour : ").append(s.total()).append(" (arrivées ").append(s.arrivals())
          .append(", départs ").append(s.departures()).append(")\n");
        sb.append("Répartition par statut : ").append(s.byStatus()).append("\n");
        sb.append(String.format(Locale.FRANCE, "Retardés : %d (%.1f %%) · Annulés : %d (%.1f %%) · En cours : %d%n",
                s.delayed().size(), s.delayRate(), s.cancelled().size(), s.cancelRate(), s.active().size()));
        if (!s.delayed().isEmpty()) {
            sb.append("Vols retardés : ").append(s.delayed().stream().limit(10)
                    .map(f -> shortFlight(f) + " (+" + delayMinutes(f) + " min)").collect(Collectors.joining("; "))).append("\n");
        }
        if (!s.byAirline().isEmpty()) sb.append("Compagnies : ").append(s.byAirline()).append("\n");
        List<Anomaly> anomalies = detectAnomalies(s.flights());
        if (!anomalies.isEmpty()) {
            sb.append("Anomalies de données détectées : ").append(anomalies.stream().limit(8)
                    .map(a -> a.flightNumber() + " : " + a.description()).collect(Collectors.joining("; "))).append("\n");
        }
        findFlightInText(question).ifPresent(f -> sb.append("\n[SOURCE: ").append(SRC_FLIGHTS)
                .append(" — fiche du vol ").append(f.getFlightNumber()).append("]\n").append(describeFlight(f)).append("\n"));
        if (canSeeUsers) {
            sb.append("\n[SOURCE: ").append(SRC_USERS).append("]\nAgents actifs par rôle : ").append(activeUsersByRole()).append("\n");
        }
        return sb.toString();
    }

    private Map<String, Long> topCounts(List<Flight> flights, java.util.function.Function<Flight, String> key) {
        return flights.stream()
                .map(key).filter(Objects::nonNull).filter(k -> !k.isBlank())
                .collect(Collectors.groupingBy(k -> k, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private static String nvl(String s) { return s == null || s.isBlank() ? "—" : s; }

    private static String iata(String s) { return isBlank(s) ? "" : " (" + s + ")"; }
}
