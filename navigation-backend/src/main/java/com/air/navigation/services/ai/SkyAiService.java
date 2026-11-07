package com.air.navigation.services.ai;

import com.air.navigation.dto.response.AiChatResponseDto;
import com.air.navigation.entities.AiConversation;
import com.air.navigation.entities.Flight;
import com.air.navigation.entities.User;
import com.air.navigation.enums.ERole;
import com.air.navigation.enums.FlightStatus;
import com.air.navigation.repository.AiConversationRepository;
import com.air.navigation.repository.UserRepository;
import com.air.navigation.services.ai.AiContextService.Anomaly;
import com.air.navigation.services.ai.AiContextService.FlightSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * SkyAI — assistant métier §8.8 / §16.
 *
 * Deux moteurs :
 *  • LLM (Spring AI / OpenAI) si une clé est configurée : le prompt reçoit les données réelles
 *    de la plateforme comme sources, plus l'historique récent de l'utilisateur.
 *  • Moteur métier interne sinon (ou en secours) : détection d'intention + réponses calculées
 *    sur les données réelles (trafic, retards, anomalies, rapport), décodage METAR,
 *    résumé extractif et classification documentaire.
 *
 * Gouvernance : aucune décision critique, sources citées, échanges journalisés,
 * accès aux données selon le rôle, validation humaine signalée pour les usages sensibles.
 */
@Service
@Slf4j
public class SkyAiService {

    enum Intent {
        GREETING, HELP, WHOAMI, FLIGHT_LOOKUP, FLIGHT_STATS, DELAYS, CANCELLATIONS, ACTIVE_FLIGHTS,
        ANOMALIES, REPORT, COMPARE, METAR, WEATHER, EMERGENCY, RUNWAY, ATC, REGULATION,
        CERTIFICATION, DOCUMENTS, SUMMARY, CLASSIFY, CHAT_HELP, STAFF, UNKNOWN
    }

    private static final String SRC_DOC4444 = "OACI Doc 4444 (PANS-ATM)";
    private static final String SRC_ANNEX11 = "OACI Annexe 11 — Services de la circulation aérienne";
    private static final String SRC_ANNEX3 = "OACI Annexe 3 — Assistance météorologique";
    private static final String SRC_DOC9432 = "OACI Doc 9432 — Manuel de radiotéléphonie";
    private static final String SRC_CDC = "Cahier des charges de la plateforme";
    private static final String SRC_ENGINE = "Moteur d'analyse SkyAI (calcul local)";

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final Pattern METAR_PATTERN = Pattern.compile(
            "\\b(?:(METAR|SPECI)\\s+)?([A-Z]{4})\\s+(\\d{2})(\\d{2})(\\d{2})Z\\b(.*)", Pattern.DOTALL);

    private static final String SYSTEM_PROMPT = """
            Tu es SkyAI, l'assistant métier de la plateforme intégrée d'assistance à la navigation aérienne (OACA / Skyguide).
            Tu aides les contrôleurs aériens, agents de piste, agents AIS, superviseurs, RH/formation et qualité.

            RÈGLES DE GOUVERNANCE (obligatoires) :
            1. Tu ne prends AUCUNE décision critique ou opérationnelle à la place d'un agent. Tu informes, synthétises et proposes.
            2. Appuie-toi en priorité sur les blocs [SOURCE: …] fournis. Pour les chiffres de la plateforme, utilise UNIQUEMENT ces blocs.
               N'invente jamais de vol, de chiffre ou de document. Si l'information manque, dis-le clairement.
            3. Pour les connaissances générales (OACI, phraséologie, météo), cite la référence (ex. « OACI Doc 4444 »).
            4. Pour toute question touchant la sécurité (urgence, piste, séparation, clairance), rappelle que la décision
               appartient à l'agent qualifié et doit être vérifiée sur les systèmes opérationnels certifiés (radar, strips, ATIS).
            5. Réponds en français, de manière concise et professionnelle. Format : phrases courtes, listes « • », **gras** pour l'essentiel.
            6. Respecte les permissions : ne divulgue que les données présentes dans le contexte fourni pour cet utilisateur.
            """;

    private static final Set<String> STOPWORDS = Set.of(
            "le", "la", "les", "de", "des", "du", "un", "une", "et", "en", "a", "au", "aux", "pour", "par", "sur",
            "dans", "est", "sont", "que", "qui", "ne", "pas", "plus", "ce", "cette", "ces", "il", "elle", "ils",
            "elles", "se", "sa", "son", "ses", "leur", "leurs", "avec", "ou", "d", "l", "s", "y", "on", "nous",
            "vous", "etre", "avoir", "ete", "fait", "the", "of", "and", "to", "is", "in", "for");

    private final AiContextService context;
    private final AiConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final ChatClient chatClient;
    private final boolean llmEnabled;
    private final String modelName;

    public SkyAiService(AiContextService context,
                        AiConversationRepository conversationRepository,
                        UserRepository userRepository,
                        ChatClient.Builder chatClientBuilder,
                        @Value("${spring.ai.openai.api-key:}") String apiKey,
                        @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}") String modelName) {
        this.context = context;
        this.conversationRepository = conversationRepository;
        this.userRepository = userRepository;
        this.chatClient = chatClientBuilder.defaultSystem(SYSTEM_PROMPT).build();
        this.llmEnabled = apiKey != null && !apiKey.isBlank() && !"not-configured".equals(apiKey);
        this.modelName = modelName;
        log.info("SkyAI — mode {}", llmEnabled ? "LLM (" + modelName + ") + moteur métier en secours" : "moteur métier interne (aucune clé OpenAI)");
    }

    public boolean isLlmEnabled() { return llmEnabled; }

    // ══════════════════════════════════════════════════════
    // POINT D'ENTRÉE
    // ══════════════════════════════════════════════════════

    @Transactional
    public AiChatResponseDto ask(Long userId, String rawMessage) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable"));
        String message = rawMessage == null ? "" : rawMessage.trim();
        if (message.isEmpty()) throw new IllegalArgumentException("Veuillez saisir une question.");
        if (message.length() > 4000) throw new IllegalArgumentException("La question ne doit pas dépasser 4000 caractères.");

        Intent intent = detectIntent(message);
        Answer answer = null;

        if (llmEnabled && intent != Intent.METAR) {
            answer = askLlm(user, message, intent);
        }
        if (answer == null) {
            answer = answerWithRules(user, message, intent);
        }

        boolean sensitive = EnumSet.of(Intent.EMERGENCY, Intent.RUNWAY, Intent.ATC, Intent.REPORT,
                Intent.CLASSIFY, Intent.ANOMALIES).contains(intent);

        AiConversation saved = conversationRepository.save(AiConversation.builder()
                .user(user)
                .question(message)
                .answer(answer.text)
                .mode(answer.mode)
                .intent(intent.name())
                .sources(String.join(" | ", answer.sources))
                .build());

        log.info("[AI-AUDIT] user={} intent={} mode={} sources={}", user.getEmail(), intent, answer.mode, answer.sources);

        return AiChatResponseDto.builder()
                .id(saved.getId())
                .question(message)
                .response(answer.text)
                .mode(answer.mode)
                .intent(intent.name())
                .sources(answer.sources)
                .suggestions(suggestionsFor(intent, user))
                .requiresHumanValidation(sensitive)
                .createdAt(saved.getCreatedAt() != null ? saved.getCreatedAt() : LocalDateTime.now())
                .build();
    }

    @Transactional(readOnly = true)
    public List<AiChatResponseDto> history(Long userId, int limit) {
        List<AiConversation> list = new ArrayList<>(
                conversationRepository.findRecentVisible(userId, PageRequest.of(0, Math.min(Math.max(limit, 1), 100))));
        Collections.reverse(list);
        return list.stream().map(c -> AiChatResponseDto.builder()
                .id(c.getId())
                .question(c.getQuestion())
                .response(c.getAnswer())
                .mode(c.getMode())
                .intent(c.getIntent())
                .sources(c.getSources() == null || c.getSources().isBlank()
                        ? List.of() : Arrays.asList(c.getSources().split(" \\| ")))
                .suggestions(List.of())
                .createdAt(c.getCreatedAt())
                .build()).collect(Collectors.toList());
    }

    @Transactional
    public void clearHistory(Long userId) {
        conversationRepository.hideAllForUser(userId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> status(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        return Map.of(
                "mode", llmEnabled ? "LLM" : "RULES",
                "model", llmEnabled ? modelName : "moteur métier interne",
                "suggestions", user == null ? List.of() : suggestionsFor(Intent.HELP, user));
    }

    // ══════════════════════════════════════════════════════
    // LLM (Spring AI)
    // ══════════════════════════════════════════════════════

    private Answer askLlm(User user, String message, Intent intent) {
        try {
            boolean canSeeUsers = canSeeStaff(user);
            String ctx = context.buildLlmContext(message, canSeeUsers);

            List<Message> history = new ArrayList<>();
            List<AiConversation> recent = new ArrayList<>(
                    conversationRepository.findRecentVisible(user.getId(), PageRequest.of(0, 6)));
            Collections.reverse(recent);
            for (AiConversation c : recent) {
                history.add(new UserMessage(c.getQuestion()));
                history.add(new AssistantMessage(c.getAnswer()));
            }

            String profile = "Utilisateur : " + user.getFullName() + " — rôles : " + roleNames(user)
                    + (user.getServiceNom() != null ? " — service : " + user.getServiceNom() : "");

            String content = chatClient.prompt()
                    .system(SYSTEM_PROMPT + "\n\n" + profile + "\n\nCONTEXTE PLATEFORME :\n" + ctx)
                    .messages(history)
                    .user(message)
                    .call()
                    .content();

            if (content == null || content.isBlank()) return null;

            List<String> sources = new ArrayList<>();
            sources.add(AiContextService.SRC_FLIGHTS + " (" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM HH:mm")) + ")");
            if (canSeeUsers) sources.add(AiContextService.SRC_USERS);
            sources.addAll(generalSourcesFor(intent));
            sources.add("Modèle IA générative " + modelName + " — à vérifier");
            return new Answer(content.trim(), "LLM", dedupe(sources));
        } catch (Exception e) {
            log.warn("SkyAI — LLM indisponible, bascule sur le moteur métier : {}", e.getMessage());
            return null;
        }
    }

    // ══════════════════════════════════════════════════════
    // DÉTECTION D'INTENTION
    // ══════════════════════════════════════════════════════

    Intent detectIntent(String message) {
        String n = normalize(message);

        if (METAR_PATTERN.matcher(message.toUpperCase()).find() && containsAny(n, "metar", "speci", "kt", "q10", "q09", "cavok", "z ")) {
            return Intent.METAR;
        }
        if (startsWithAny(n, "resume", "resumer", "synthetise", "fais un resume", "peux-tu resumer", "summarize")
                && message.length() > 140) {
            return Intent.SUMMARY;
        }
        if (startsWithAny(n, "classe", "classifie", "classer", "quelle categorie", "categorise") && message.length() > 60) {
            return Intent.CLASSIFY;
        }

        Map<Intent, String[]> kw = new LinkedHashMap<>();
        kw.put(Intent.REPORT, new String[]{"rapport", "redige", "rediger", "compte rendu", "compte-rendu", "bilan"});
        kw.put(Intent.COMPARE, new String[]{"compar", "par rapport a hier", "evolution", "tendance", "hier"});
        kw.put(Intent.ANOMALIES, new String[]{"anomal", "incoheren", "erreur de saisie", "verifie les donnees", "qualite des donnees", "controle"});
        kw.put(Intent.DELAYS, new String[]{"retard", "delay", "en retard"});
        kw.put(Intent.CANCELLATIONS, new String[]{"annul", "cancel"});
        kw.put(Intent.ACTIVE_FLIGHTS, new String[]{"en vol", "actifs", "en cours", "en approche", "embarquement", "decollage"});
        kw.put(Intent.FLIGHT_STATS, new String[]{"statisti", "combien", "nombre", "trafic", "aujourd'hui", "aujourdhui", "kpi", "indicateur", "pointe", "heure de pointe", "compagnie", "type d'avion", "appareil"});
        kw.put(Intent.EMERGENCY, new String[]{"urgence", "mayday", "pan pan", "panpan", "detresse", "incendie", "evacu", "7700", "7600", "7500", "detournement"});
        kw.put(Intent.RUNWAY, new String[]{"piste", "runway", "qfu", "atterrissage", "turbulence de sillage", "sillage", "wake"});
        kw.put(Intent.ATC, new String[]{"atc", "clairance", "clearance", "separation", "radar", "squawk", "transpondeur", "collation", "read-back", "readback", "phraseologie", "tcas"});
        kw.put(Intent.WEATHER, new String[]{"meteo", "vent", "visibilite", "taf", "atis", "nuage", "brouillard", "orage", "cisaillement", "qnh"});
        kw.put(Intent.REGULATION, new String[]{"reglement", "oaci", "icao", "annexe", "doc 4444", "sera", "aip", "dgac", "norme"});
        kw.put(Intent.CERTIFICATION, new String[]{"certif", "licence", "habilitation", "qualification", "medical", "aptitude", "anglais", "formation", "recyclage", "expiration", "echeance"});
        kw.put(Intent.DOCUMENTS, new String[]{"document", "procedure", "notam", "sup aip", "aic", "circulaire", "note de service", "ged"});
        kw.put(Intent.CHAT_HELP, new String[]{"message prive", "conversation privee", "chat", "salon", "groupe", "envoyer un message", "messagerie"});
        kw.put(Intent.STAFF, new String[]{"agents", "effectif", "utilisateurs", "personnel", "combien d'agents"});
        kw.put(Intent.WHOAMI, new String[]{"qui suis", "mon role", "mes droits", "mon profil", "mes permissions"});
        kw.put(Intent.HELP, new String[]{"aide", "help", "que sais-tu", "que peux-tu", "tes capacites", "fonctionnalites", "comment ca marche"});
        kw.put(Intent.GREETING, new String[]{"bonjour", "salut", "hello", "bonsoir", "coucou", "salam", "aslema", "hi "});

        // Sécurité d'abord : toute mention d'urgence l'emporte sur les autres intentions
        for (String k : kw.get(Intent.EMERGENCY)) {
            if (n.contains(k)) return Intent.EMERGENCY;
        }

        Intent best = Intent.UNKNOWN;
        int bestScore = 0;
        for (Map.Entry<Intent, String[]> e : kw.entrySet()) {
            int score = 0;
            for (String k : e.getValue()) if (n.contains(k)) score += k.length() > 6 ? 2 : 1;
            if (score > bestScore) { bestScore = score; best = e.getKey(); }
        }

        // Un numéro de vol existant dans la question prime sur les intentions génériques
        if (EnumSet.of(Intent.UNKNOWN, Intent.FLIGHT_STATS, Intent.DELAYS, Intent.ACTIVE_FLIGHTS, Intent.CANCELLATIONS, Intent.GREETING).contains(best)
                && context.findFlightInText(message).isPresent()) {
            return Intent.FLIGHT_LOOKUP;
        }
        // « bonjour, combien de vols… » → la vraie question n'est pas la salutation
        if (best == Intent.GREETING && n.length() > 40) {
            int second = 0; Intent alt = Intent.UNKNOWN;
            for (Map.Entry<Intent, String[]> e : kw.entrySet()) {
                if (e.getKey() == Intent.GREETING) continue;
                int s = 0;
                for (String k : e.getValue()) if (n.contains(k)) s++;
                if (s > second) { second = s; alt = e.getKey(); }
            }
            if (alt != Intent.UNKNOWN) return alt;
        }
        return best;
    }

    // ══════════════════════════════════════════════════════
    // MOTEUR MÉTIER
    // ══════════════════════════════════════════════════════

    private Answer answerWithRules(User user, String message, Intent intent) {
        try {
            return switch (intent) {
                case GREETING -> greeting(user);
                case HELP -> help(user);
                case WHOAMI -> whoAmI(user);
                case FLIGHT_LOOKUP -> flightLookup(message);
                case FLIGHT_STATS -> flightStats();
                case DELAYS -> delays();
                case CANCELLATIONS -> cancellations();
                case ACTIVE_FLIGHTS -> activeFlights();
                case ANOMALIES -> anomalies();
                case REPORT -> dailyReport(user);
                case COMPARE -> compareWithYesterday();
                case METAR -> decodeMetar(message);
                case WEATHER -> weather();
                case EMERGENCY -> emergency();
                case RUNWAY -> runway();
                case ATC -> atc();
                case REGULATION -> regulation();
                case CERTIFICATION -> certification(user);
                case DOCUMENTS -> documents();
                case SUMMARY -> summarize(message);
                case CLASSIFY -> classify(message);
                case CHAT_HELP -> chatHelp();
                case STAFF -> staff(user);
                default -> unknown(message);
            };
        } catch (Exception e) {
            log.error("SkyAI — erreur moteur métier ({}) : {}", intent, e.getMessage(), e);
            return new Answer("⚠️ Je n'ai pas pu analyser les données pour le moment. Réessayez ou consultez directement le tableau de bord.",
                    "RULES", List.of(SRC_ENGINE));
        }
    }

    private Answer greeting(User user) {
        String first = user.getFirstName() != null ? capitalize(user.getFirstName()) : "";
        FlightSnapshot s = context.today();
        String brief = s.total() == 0
                ? "Aucun vol n'est enregistré pour aujourd'hui pour le moment."
                : String.format(Locale.FRANCE, "Aujourd'hui : **%d vols** dont **%d retardé(s)** et **%d annulé(s)**.",
                        s.total(), s.delayed().size(), s.cancelled().size());
        return new Answer("✈️ Bonjour " + first + " ! Je suis **SkyAI**, votre assistant métier.\n" + brief
                + "\n\nJe peux analyser le trafic, détecter des anomalies de saisie, rédiger un rapport, décoder un METAR, "
                + "résumer ou classer un texte, et rappeler les procédures OACI. Que souhaitez-vous ?",
                "RULES", List.of(AiContextService.SRC_FLIGHTS));
    }

    private Answer help(User user) {
        StringBuilder sb = new StringBuilder("💡 **Ce que SkyAI sait faire pour vous :**\n");
        sb.append("• 📊 **Trafic du jour** — « combien de vols aujourd'hui ? », « heure de pointe »\n");
        sb.append("• ⏱️ **Retards / annulations** — « quels vols sont en retard ? »\n");
        sb.append("• 🔎 **Fiche vol** — « statut du TU712 »\n");
        sb.append("• 🧪 **Anomalies de saisie** — « vérifie la cohérence des données »\n");
        sb.append("• 📝 **Rapport journalier** — « rédige le rapport du jour »\n");
        sb.append("• 📈 **Comparaison** — « compare avec hier »\n");
        sb.append("• 🌤️ **Décodage METAR** — collez un METAR brut\n");
        sb.append("• 📄 **Résumé / classification** — « résume : <texte> », « classe ce document : <texte> »\n");
        sb.append("• 🗼 **Procédures** — urgences, pistes, ATC, réglementation OACI\n");
        if (canSeeStaff(user)) sb.append("• 👥 **Effectifs** — « combien d'agents par rôle ? »\n");
        sb.append("\n_SkyAI assiste mais ne décide pas : toute décision opérationnelle reste celle de l'agent qualifié._");
        return new Answer(sb.toString(), "RULES", List.of(SRC_CDC + " §8.8"));
    }

    private Answer whoAmI(User user) {
        List<String> roles = roleNames(user);
        StringBuilder sb = new StringBuilder("👤 **Votre profil**\n");
        sb.append("• Nom : ").append(user.getFullName()).append("\n");
        sb.append("• Rôle(s) : ").append(String.join(", ", roles)).append("\n");
        if (user.getServiceNom() != null) sb.append("• Service : ").append(user.getServiceNom()).append("\n");
        if (user.getGrade() != null) sb.append("• Grade : ").append(user.getGrade()).append("\n");
        sb.append("\n**Accès SkyAI :** données de trafic");
        sb.append(canSeeStaff(user) ? ", effectifs par rôle" : "");
        sb.append(". Les données hors de vos permissions ne sont pas utilisées dans mes réponses.");
        return new Answer(sb.toString(), "RULES", List.of(AiContextService.SRC_USERS));
    }

    private Answer flightLookup(String message) {
        Optional<Flight> f = context.findFlightInText(message);
        if (f.isEmpty()) {
            return new Answer("🔎 Je n'ai trouvé aucun vol correspondant dans la base. Vérifiez le numéro (ex. **TU712**).",
                    "RULES", List.of(AiContextService.SRC_FLIGHTS));
        }
        Flight flight = f.get();
        StringBuilder sb = new StringBuilder("✈️ ").append(context.describeFlight(flight));
        List<Anomaly> an = context.detectAnomalies(List.of(flight));
        if (!an.isEmpty()) {
            sb.append("\n\n⚠️ **Points à vérifier :**\n");
            an.forEach(a -> sb.append("• ").append(a.description()).append("\n"));
        }
        return new Answer(sb.toString().trim(), "RULES",
                List.of(AiContextService.SRC_FLIGHTS + " — fiche " + flight.getFlightNumber()));
    }

    private Answer flightStats() {
        FlightSnapshot s = context.today();
        if (s.total() == 0) {
            return new Answer("📊 Aucun vol programmé aujourd'hui (" + LocalDate.now().format(DMY) + "). "
                    + "La base contient " + context.totalFlightsInDatabase() + " vol(s) au total.",
                    "RULES", List.of(AiContextService.SRC_FLIGHTS));
        }
        StringBuilder sb = new StringBuilder();
        sb.append("📊 **Trafic du ").append(LocalDate.now().format(DMY)).append("**\n");
        sb.append("• Total : **").append(s.total()).append(" vols** (").append(s.arrivals()).append(" arrivées · ")
          .append(s.departures()).append(" départs)\n");
        sb.append(String.format(Locale.FRANCE, "• Retards : **%d** (%.1f %%) · Annulations : **%d** (%.1f %%)%n",
                s.delayed().size(), s.delayRate(), s.cancelled().size(), s.cancelRate()));
        sb.append("• En cours : ").append(s.active().size()).append("\n");

        peakHour(s).ifPresent(p -> sb.append("• Heure de pointe : **").append(p).append("**\n"));
        if (!s.byAirline().isEmpty()) sb.append("• Compagnies : ").append(formatCounts(s.byAirline())).append("\n");
        if (!s.byAircraft().isEmpty()) sb.append("• Appareils : ").append(formatCounts(s.byAircraft())).append("\n");

        sb.append("\n**Par statut :** ").append(s.byStatus().entrySet().stream()
                .map(e -> statusLabel(e.getKey()) + " " + e.getValue()).collect(Collectors.joining(" · ")));

        if (s.delayRate() > 20) {
            sb.append("\n\n🔔 Le taux de retard dépasse 20 % : une analyse des causes peut être utile (météo, rotations, piste).");
        }
        return new Answer(sb.toString(), "RULES", List.of(AiContextService.SRC_FLIGHTS, SRC_ENGINE));
    }

    private Answer delays() {
        FlightSnapshot s = context.today();
        if (s.delayed().isEmpty()) {
            return new Answer("✅ Aucun vol en retard aujourd'hui (seuil : statut RETARDÉ ou écart ≥ 15 min).",
                    "RULES", List.of(AiContextService.SRC_FLIGHTS));
        }
        StringBuilder sb = new StringBuilder(String.format(Locale.FRANCE,
                "⏱️ **%d vol(s) en retard** aujourd'hui (%.1f %% du trafic) :%n", s.delayed().size(), s.delayRate()));
        s.delayed().stream()
                .sorted(Comparator.comparingLong(context::delayMinutes).reversed())
                .limit(10)
                .forEach(f -> {
                    long d = context.delayMinutes(f);
                    sb.append("• **").append(f.getFlightNumber()).append("** ")
                      .append(Optional.ofNullable(f.getAirline()).orElse("")).append(" — prévu ")
                      .append(f.getScheduledTime() != null ? f.getScheduledTime().format(HM) : "?")
                      .append(d > 0 ? " · +" + d + " min" : " · durée non communiquée")
                      .append(f.getRemarks() != null && !f.getRemarks().isBlank() ? " · " + f.getRemarks() : "")
                      .append("\n");
                });
        double avg = s.delayed().stream().mapToLong(context::delayMinutes).filter(m -> m > 0).average().orElse(0);
        if (avg > 0) sb.append(String.format(Locale.FRANCE, "%nRetard moyen : **%.0f min**.", avg));
        return new Answer(sb.toString().trim(), "RULES", List.of(AiContextService.SRC_FLIGHTS, SRC_ENGINE));
    }

    private Answer cancellations() {
        FlightSnapshot s = context.today();
        if (s.cancelled().isEmpty()) {
            return new Answer("✅ Aucune annulation aujourd'hui.", "RULES", List.of(AiContextService.SRC_FLIGHTS));
        }
        StringBuilder sb = new StringBuilder("❌ **" + s.cancelled().size() + " vol(s) annulé(s)** aujourd'hui :\n");
        s.cancelled().forEach(f -> sb.append("• ").append(context.shortFlight(f))
                .append(f.getRemarks() != null && !f.getRemarks().isBlank() ? " — " + f.getRemarks() : "").append("\n"));
        return new Answer(sb.toString().trim(), "RULES", List.of(AiContextService.SRC_FLIGHTS));
    }

    private Answer activeFlights() {
        FlightSnapshot s = context.today();
        if (s.active().isEmpty()) {
            return new Answer("🛫 Aucun vol en cours (embarquement, décollage, en vol, approche ou au sol) actuellement.",
                    "RULES", List.of(AiContextService.SRC_FLIGHTS));
        }
        StringBuilder sb = new StringBuilder("🛫 **" + s.active().size() + " vol(s) en cours :**\n");
        s.active().stream().limit(15).forEach(f -> sb.append("• ").append(context.shortFlight(f))
                .append(" — ").append(statusLabel(f.getStatus()))
                .append(f.getRunway() != null && !f.getRunway().isBlank() ? " · piste " + f.getRunway() : "").append("\n"));
        sb.append("\nDonnées indicatives : la situation temps réel fait foi sur les systèmes opérationnels.");
        return new Answer(sb.toString().trim(), "RULES", List.of(AiContextService.SRC_FLIGHTS));
    }

    private Answer anomalies() {
        FlightSnapshot s = context.today();
        List<Anomaly> list = context.detectAnomalies(s.flights());
        if (list.isEmpty()) {
            return new Answer("🧪 **Contrôle de cohérence** sur " + s.total() + " vol(s) du jour : aucune anomalie détectée. ✅",
                    "RULES", List.of(AiContextService.SRC_FLIGHTS, SRC_ENGINE));
        }
        StringBuilder sb = new StringBuilder("🧪 **" + list.size() + " point(s) à vérifier** sur les données du jour :\n");
        list.stream().limit(20).forEach(a -> sb.append("• **").append(a.flightNumber()).append("** : ").append(a.description()).append("\n"));
        sb.append("\nCes alertes sont **non critiques** (qualité des données). Merci de confirmer ou corriger dans le module Vols.");
        return new Answer(sb.toString().trim(), "RULES", List.of(AiContextService.SRC_FLIGHTS, SRC_ENGINE));
    }

    private Answer dailyReport(User user) {
        FlightSnapshot s = context.today();
        List<Anomaly> an = context.detectAnomalies(s.flights());
        StringBuilder sb = new StringBuilder();
        sb.append("📝 **Projet de rapport d'activité — ").append(LocalDate.now().format(DMY)).append("**\n\n");
        sb.append("**1. Synthèse du trafic**\n");
        sb.append("• ").append(s.total()).append(" vols traités : ").append(s.arrivals()).append(" arrivées, ")
          .append(s.departures()).append(" départs.\n");
        peakHour(s).ifPresent(p -> sb.append("• Pointe de trafic : ").append(p).append(".\n"));
        if (!s.byAirline().isEmpty()) sb.append("• Principales compagnies : ").append(formatCounts(s.byAirline())).append(".\n");
        sb.append("\n**2. Ponctualité**\n");
        sb.append(String.format(Locale.FRANCE, "• Retards : %d (%.1f %%) — Annulations : %d (%.1f %%).%n",
                s.delayed().size(), s.delayRate(), s.cancelled().size(), s.cancelRate()));
        s.delayed().stream().sorted(Comparator.comparingLong(context::delayMinutes).reversed()).limit(5)
                .forEach(f -> sb.append("  – ").append(f.getFlightNumber()).append(" : +").append(context.delayMinutes(f)).append(" min\n"));
        sb.append("\n**3. Qualité des données**\n");
        sb.append(an.isEmpty() ? "• Aucune incohérence détectée.\n" : "• " + an.size() + " point(s) à vérifier (voir « anomalies »).\n");
        sb.append("\n**4. Événements / observations**\n• _À compléter par l'agent : incidents, travaux, météo, coordination…_\n");
        sb.append("\n**5. Validation**\n• Rédigé avec l'assistance de SkyAI pour ").append(user.getFullName())
          .append(".\n• ⚠️ _Document à relire et valider par le superviseur avant diffusion._");
        return new Answer(sb.toString(), "RULES", List.of(AiContextService.SRC_FLIGHTS, SRC_ENGINE));
    }

    private Answer compareWithYesterday() {
        FlightSnapshot t = context.today();
        FlightSnapshot y = context.snapshot(LocalDate.now().minusDays(1));
        StringBuilder sb = new StringBuilder("📈 **Aujourd'hui vs hier**\n");
        sb.append("• Vols : ").append(t.total()).append(" vs ").append(y.total()).append(" ").append(trend(t.total(), y.total())).append("\n");
        sb.append("• Retards : ").append(t.delayed().size()).append(" vs ").append(y.delayed().size()).append(" ")
          .append(trend(t.delayed().size(), y.delayed().size())).append("\n");
        sb.append("• Annulations : ").append(t.cancelled().size()).append(" vs ").append(y.cancelled().size()).append(" ")
          .append(trend(t.cancelled().size(), y.cancelled().size())).append("\n");
        sb.append(String.format(Locale.FRANCE, "• Taux de retard : %.1f %% vs %.1f %%%n", t.delayRate(), y.delayRate()));
        sb.append("\n_La journée en cours n'est peut-être pas terminée : comparaison indicative._");
        return new Answer(sb.toString(), "RULES", List.of(AiContextService.SRC_FLIGHTS, SRC_ENGINE));
    }

    // ── Météo ────────────────────────────────────────────────────────
    private Answer decodeMetar(String message) {
        Matcher m = METAR_PATTERN.matcher(message.toUpperCase().replaceAll("\\s+", " "));
        if (!m.find()) return weather();
        String station = m.group(2);
        String body = m.group(6).trim().split("=")[0];
        String[] tokens = body.split(" ");

        StringBuilder sb = new StringBuilder("🌤️ **Décodage " + (m.group(1) != null ? m.group(1) : "METAR") + " — " + station + "**\n");
        sb.append("• Observation : jour ").append(m.group(3)).append(" à ").append(m.group(4)).append(":").append(m.group(5)).append(" UTC\n");

        List<String> clouds = new ArrayList<>();
        List<String> wx = new ArrayList<>();
        for (String tk : tokens) {
            if (tk.matches("(\\d{3}|VRB)(\\d{2,3})(G\\d{2,3})?KT")) {
                Matcher w = Pattern.compile("(\\d{3}|VRB)(\\d{2,3})(?:G(\\d{2,3}))?KT").matcher(tk);
                if (w.matches()) {
                    sb.append("• Vent : ").append("VRB".equals(w.group(1)) ? "variable" : w.group(1) + "°")
                      .append(" à **").append(Integer.parseInt(w.group(2))).append(" kt**");
                    if (w.group(3) != null) sb.append(", rafales **").append(Integer.parseInt(w.group(3))).append(" kt**");
                    sb.append("\n");
                }
            } else if (tk.matches("\\d{3}V\\d{3}")) {
                sb.append("• Direction variable entre ").append(tk, 0, 3).append("° et ").append(tk.substring(4)).append("°\n");
            } else if ("CAVOK".equals(tk)) {
                sb.append("• **CAVOK** : visibilité ≥ 10 km, pas de nuage significatif, pas de phénomène\n");
            } else if (tk.matches("\\d{4}")) {
                int v = Integer.parseInt(tk);
                sb.append("• Visibilité : ").append(v == 9999 ? "**≥ 10 km**" : "**" + v + " m**").append("\n");
            } else if (tk.matches("(FEW|SCT|BKN|OVC)\\d{3}(CB|TCU)?")) {
                String cov = switch (tk.substring(0, 3)) {
                    case "FEW" -> "peu (1-2/8)"; case "SCT" -> "épars (3-4/8)";
                    case "BKN" -> "fragmenté (5-7/8)"; default -> "couvert (8/8)";
                };
                int base = Integer.parseInt(tk.substring(3, 6)) * 100;
                clouds.add(cov + " à " + base + " ft" + (tk.endsWith("CB") ? " — **cumulonimbus**" : tk.endsWith("TCU") ? " — cumulus bourgeonnant" : ""));
            } else if (tk.matches("NSC|SKC|NCD")) {
                clouds.add("pas de nuage significatif");
            } else if (tk.matches("(M?\\d{2})/(M?\\d{2})")) {
                String[] td = tk.split("/");
                sb.append("• Température **").append(td[0].replace("M", "-")).append(" °C**, point de rosée ")
                  .append(td[1].replace("M", "-")).append(" °C\n");
            } else if (tk.matches("Q\\d{4}")) {
                sb.append("• QNH : **").append(Integer.parseInt(tk.substring(1))).append(" hPa**\n");
            } else if (tk.matches("A\\d{4}")) {
                sb.append("• Altimètre : ").append(tk.substring(1, 3)).append(".").append(tk.substring(3)).append(" inHg\n");
            } else if (tk.matches("[+-]?(VC)?(MI|BC|SH|TS|FZ|DR|BL)?(DZ|RA|SN|SG|GR|GS|BR|FG|HZ|FU|SA|DU|SQ|FC|DS|SS)+")) {
                wx.add(decodeWeather(tk));
            } else if ("NOSIG".equals(tk)) {
                sb.append("• Tendance : **NOSIG** (pas de changement significatif prévu)\n");
            } else if (tk.startsWith("TEMPO") || tk.startsWith("BECMG")) {
                sb.append("• Tendance : ").append("TEMPO".equals(tk) ? "variations temporaires prévues" : "évolution prévue").append("\n");
            }
        }
        if (!wx.isEmpty()) sb.append("• Temps présent : ").append(String.join(", ", wx)).append("\n");
        if (!clouds.isEmpty()) sb.append("• Nuages : ").append(String.join(" ; ", clouds)).append("\n");

        boolean ifr = body.matches(".*\\b(0\\d{3}|1\\d{3}|2\\d{3}|3\\d{3}|4\\d{3})\\b.*")
                || body.matches(".*\\b(BKN|OVC)00\\d.*") || body.contains("FG") || body.contains("TS");
        sb.append("\n").append(ifr
                ? "⚠️ Conditions potentiellement **dégradées** (visibilité réduite, plafond bas, orage ou brouillard) : vérifier les minima et l'ATIS."
                : "✅ Pas de dégradation majeure apparente dans ce message.");
        sb.append("\n_Décodage automatique indicatif — le bulletin officiel fait foi._");
        return new Answer(sb.toString(), "RULES", List.of(SRC_ANNEX3, SRC_ENGINE));
    }

    private String decodeWeather(String tk) {
        StringBuilder s = new StringBuilder();
        if (tk.startsWith("+")) s.append("fort ");
        else if (tk.startsWith("-")) s.append("faible ");
        Map<String, String> codes = new LinkedHashMap<>();
        codes.put("VC", "au voisinage"); codes.put("TS", "orage"); codes.put("SH", "averses"); codes.put("FZ", "se congelant");
        codes.put("MI", "mince"); codes.put("BC", "bancs"); codes.put("BL", "chasse-"); codes.put("DR", "chasse basse");
        codes.put("DZ", "bruine"); codes.put("RA", "pluie"); codes.put("SN", "neige"); codes.put("GR", "grêle");
        codes.put("GS", "grésil"); codes.put("BR", "brume"); codes.put("FG", "brouillard"); codes.put("HZ", "brume sèche");
        codes.put("FU", "fumée"); codes.put("SA", "sable"); codes.put("DU", "poussière"); codes.put("SQ", "grains");
        codes.put("DS", "tempête de poussière"); codes.put("SS", "tempête de sable");
        String rest = tk.replaceFirst("^[+-]", "");
        List<String> parts = new ArrayList<>();
        for (int i = 0; i + 2 <= rest.length(); i += 2) {
            parts.add(codes.getOrDefault(rest.substring(i, i + 2), rest.substring(i, i + 2)));
        }
        return s.append(String.join(" ", parts)).toString().trim();
    }

    private Answer weather() {
        return new Answer("""
                🌤️ **Informations météorologiques**
                • Collez un **METAR** brut (ex. « METAR DTTA 071030Z 33012KT 9999 FEW020 22/14 Q1015 NOSIG ») et je le décode.
                • **TAF** : prévision d'aérodrome (9 à 30 h) — **ATIS** : diffusion locale continue.
                • Seuils usuels : visibilité < 5 km ou plafond < 1500 ft → conditions dégradées (IMC probable).
                • Cisaillement, orage (TS/CB) : informer immédiatement le superviseur et les équipages concernés.
                _La plateforme ne reçoit pas encore de flux météo en direct : consultez le bulletin officiel._""",
                "RULES", List.of(SRC_ANNEX3));
    }

    // ── Procédures ───────────────────────────────────────────────────
    private Answer emergency() {
        return new Answer("""
                🚨 **Rappel — urgences (OACI)**
                **MAYDAY (détresse)** : « MAYDAY ×3, indicatif, nature, intentions, position, niveau ».
                • Priorité absolue, dégager le trafic, fréquence/secteur dédiés si possible.
                • Alerter le **SSLIA**, le superviseur et l'organisme de recherche et sauvetage si nécessaire.
                **PAN PAN (urgence)** : même logique, priorité élevée sans danger immédiat.
                **Codes transpondeur** : 7700 urgence · 7600 panne radio · 7500 intervention illicite.
                • Consigner l'événement (heure, actions, intervenants) pour le compte rendu.

                ⚠️ _Rappel de procédure uniquement : la conduite de l'urgence relève du contrôleur qualifié et des consignes locales._""",
                "RULES", List.of(SRC_DOC4444, SRC_ANNEX11));
    }

    private Answer runway() {
        FlightSnapshot s = context.today();
        Map<String, Long> runways = s.flights().stream()
                .map(Flight::getRunway).filter(Objects::nonNull).filter(r -> !r.isBlank())
                .collect(Collectors.groupingBy(r -> r, TreeMap::new, Collectors.counting()));
        StringBuilder sb = new StringBuilder("🛬 **Pistes**\n");
        if (!runways.isEmpty()) sb.append("• Utilisation aujourd'hui (plateforme) : ").append(formatCounts(runways)).append("\n");
        sb.append("""
                • Choix du QFU : piste la plus face au vent, selon consignes locales et NOTAM.
                • Turbulence de sillage (approche, distance) : Heavy → Medium **5 NM**, Heavy → Light **6 NM**, Medium → Light **5 NM**.
                • Entrée / alignement / décollage : clairance explicite + **collationnement** obligatoire.
                • Atterrissage : piste confirmée libre avant toute autorisation.
                ⚠️ _Valeurs indicatives : appliquer les minima publiés et les consignes de l'organisme._""");
        List<String> src = new ArrayList<>(List.of(SRC_DOC4444));
        if (!runways.isEmpty()) src.add(0, AiContextService.SRC_FLIGHTS);
        return new Answer(sb.toString(), "RULES", src);
    }

    private Answer atc() {
        return new Answer("""
                🗼 **Procédures ATC — rappels**
                • Séparation verticale : **1000 ft** (RVSM) ; horizontale radar : **5 NM** en route, **3 NM** en approche selon autorisation.
                • **Collationnement** obligatoire : clairances de piste, niveaux, caps, vitesses, fréquences, codes transpondeur.
                • **TCAS RA** : le pilote suit l'avis de résolution ; le contrôleur ne donne pas d'instruction contraire et reprend après « clear of conflict ».
                • Transfert : coordination avec le secteur suivant avant la limite.
                • Phraséologie normalisée : anglais OACI pour les vols internationaux.
                ⚠️ _Rappel documentaire — la situation radar et les consignes locales font foi._""",
                "RULES", List.of(SRC_DOC4444, SRC_DOC9432));
    }

    private Answer regulation() {
        return new Answer("""
                📋 **Références réglementaires**
                • **Annexe 2** — Règles de l'air
                • **Annexe 3** — Assistance météorologique
                • **Annexe 11** — Services de la circulation aérienne
                • **Annexe 15** — Services d'information aéronautique (AIP, NOTAM, SUP, AIC)
                • **Doc 4444 (PANS-ATM)** — procédures ATC
                • **Doc 9432** — radiotéléphonie
                • **AIP Tunisie** — dispositions nationales
                Indiquez un thème (séparation, NOTAM, sillage…) pour un rappel ciblé.""",
                "RULES", List.of("OACI — Annexes 2, 3, 11, 15", SRC_DOC4444, SRC_DOC9432));
    }

    private Answer certification(User user) {
        String extra = hasRole(user, ERole.RH_FORMATION) || hasRole(user, ERole.ADMIN)
                ? "\nEn tant que responsable, vous pourrez y configurer les **alertes avant expiration** (30/60/90 jours)."
                : "";
        return new Answer("""
                🎓 **Certifications & habilitations**
                Le module de suivi (§8.5) n'est pas encore alimenté sur la plateforme : je ne peux donc pas lister vos échéances réelles.
                Éléments qui seront suivis :
                • Licence et qualifications · habilitations **tour** / **radar**
                • **Anglais aéronautique** (niveau OACI 4 : revalidation tous les 3 ans, niveau 5 : 6 ans, niveau 6 : sans limite)
                • Aptitude médicale · formation sécurité · recyclage périodique
                En attendant, rapprochez-vous du service RH / Formation pour vos dates de validité.""" + extra,
                "RULES", List.of(SRC_CDC + " §8.5", "OACI Annexe 1 — Licences du personnel"));
    }

    private Answer documents() {
        return new Answer("""
                📄 **Documentation aéronautique**
                • **AIP** : publication permanente · **SUP AIP** : modifications temporaires · **AIC** : informations générales
                • **NOTAM** : information urgente/temporaire (fermeture de piste, obstacle, balisage…)
                • La GED de la plateforme (dépôt, versions, accusés de lecture) est en cours d'intégration.
                Je peux déjà : **résumer** un texte (« résume : … ») ou **proposer une classification** (« classe ce document : … »).""",
                "RULES", List.of("OACI Annexe 15", SRC_CDC + " §8.3 / §8.6"));
    }

    private Answer chatHelp() {
        return new Answer("""
                💬 **Messagerie interne**
                • **Conversation privée** : menu Communications → icône 💬+ → choisissez un agent.
                • **Groupe** : icône 👥+ → nom, membres → Créer.
                • **Salons de service** : # atc, # piste, # ais… accessibles aux agents du service, superviseurs et admins.
                • Pièces jointes (25 Mo max), messages épinglés 📌, recherche dans l'historique 🔍.
                Tous les échanges sont horodatés et journalisés.""",
                "RULES", List.of(SRC_CDC + " §8.7"));
    }

    private Answer staff(User user) {
        if (!canSeeStaff(user)) {
            return new Answer("🔒 Les effectifs sont réservés aux profils Administrateur, Superviseur et RH / Formation.",
                    "RULES", List.of(SRC_CDC + " §11"));
        }
        Map<String, Long> byRole = context.activeUsersByRole();
        long total = byRole.values().stream().mapToLong(Long::longValue).sum();
        StringBuilder sb = new StringBuilder("👥 **Agents actifs par rôle**\n");
        byRole.forEach((k, v) -> sb.append("• ").append(k).append(" : ").append(v).append("\n"));
        sb.append("(un agent peut cumuler plusieurs rôles — ").append(total).append(" affectations)");
        return new Answer(sb.toString(), "RULES", List.of(AiContextService.SRC_USERS));
    }

    // ── Résumé extractif ─────────────────────────────────────────────
    private Answer summarize(String message) {
        String text = stripCommand(message);
        List<String> sentences = Arrays.stream(text.split("(?<=[.!?;])\\s+|\\n+"))
                .map(String::trim).filter(s -> s.length() > 25).toList();
        if (sentences.size() <= 2) {
            return new Answer("📄 Le texte est déjà court : " + text, "RULES", List.of(SRC_ENGINE));
        }
        Map<String, Integer> freq = new HashMap<>();
        for (String w : normalize(text).split("[^a-z0-9]+")) {
            if (w.length() > 2 && !STOPWORDS.contains(w)) freq.merge(w, 1, Integer::sum);
        }
        record Scored(int idx, String s, double score) {}
        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < sentences.size(); i++) {
            String[] words = normalize(sentences.get(i)).split("[^a-z0-9]+");
            double sc = Arrays.stream(words).mapToInt(w -> freq.getOrDefault(w, 0)).sum() / Math.pow(Math.max(words.length, 1), 0.4);
            if (i == 0) sc *= 1.2;
            scored.add(new Scored(i, sentences.get(i), sc));
        }
        int keep = Math.min(Math.max(2, sentences.size() / 4), 5);
        List<Scored> top = scored.stream().sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(keep).sorted(Comparator.comparingInt(Scored::idx)).toList();

        StringBuilder sb = new StringBuilder("📄 **Résumé** (" + keep + " phrases clés sur " + sentences.size() + ")\n");
        top.forEach(s -> sb.append("• ").append(s.s()).append("\n"));

        List<String> keys = extractKeyInfo(text);
        if (!keys.isEmpty()) sb.append("\n**Informations clés détectées :** ").append(String.join(" · ", keys));
        List<String> topWords = freq.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(6).map(Map.Entry::getKey).toList();
        sb.append("\n**Mots-clés :** ").append(String.join(", ", topWords));
        sb.append("\n_Résumé extractif automatique — se référer au document source._");
        return new Answer(sb.toString(), "RULES", List.of("Texte fourni par l'utilisateur", SRC_ENGINE));
    }

    // ── Classification documentaire ──────────────────────────────────
    private Answer classify(String message) {
        String text = normalize(stripCommand(message));
        Map<String, String[]> cats = new LinkedHashMap<>();
        cats.put("NOTAM", new String[]{"notam", "fermeture", "ferme", "obstacle", "balisage", "hors service", "u/s", "clsd", "grue"});
        cats.put("SUP AIP (supplément temporaire)", new String[]{"supplement", "sup aip", "temporaire", "du ", "au ", "periode"});
        cats.put("AIC (circulaire d'information)", new String[]{"aic", "circulaire d'information", "information generale", "recommandation"});
        cats.put("AIP (publication permanente)", new String[]{"aip", "gen ", "enr ", "ad 2", "publication d'information"});
        cats.put("Note de service", new String[]{"note de service", "il est porte a la connaissance", "personnel", "a compter du", "direction"});
        cats.put("Procédure interne", new String[]{"procedure", "etape", "checklist", "consigne", "doit", "verifier", "responsable"});
        cats.put("Document qualité", new String[]{"qualite", "audit", "non-conformite", "non conformite", "indicateur", "iso", "action corrective"});
        cats.put("Compte rendu d'événement", new String[]{"incident", "evenement", "occurrence", "rapport", "temoin", "deroulement"});

        Map<String, Integer> scores = new LinkedHashMap<>();
        cats.forEach((cat, kws) -> {
            int sc = 0;
            for (String k : kws) if (text.contains(k)) sc++;
            scores.put(cat, sc);
        });
        int total = scores.values().stream().mapToInt(Integer::intValue).sum();
        List<Map.Entry<String, Integer>> ranked = scores.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).toList();

        if (ranked.isEmpty()) {
            return new Answer("🏷️ Je ne reconnais pas de catégorie documentaire claire. Ajoutez plus de contenu ou précisez le type.",
                    "RULES", List.of(SRC_ENGINE));
        }
        StringBuilder sb = new StringBuilder("🏷️ **Classification proposée : ").append(ranked.get(0).getKey()).append("**\n");
        ranked.stream().limit(3).forEach(e -> sb.append(String.format(Locale.FRANCE, "• %s — %.0f %%%n",
                e.getKey(), 100.0 * e.getValue() / total)));
        List<String> keys = extractKeyInfo(stripCommand(message));
        if (!keys.isEmpty()) sb.append("\n**Éléments extraits :** ").append(String.join(" · ", keys));
        sb.append("\n⚠️ _Proposition automatique : la classification définitive revient à l'agent AIS._");
        return new Answer(sb.toString(), "RULES", List.of("Texte fourni par l'utilisateur", "OACI Annexe 15", SRC_ENGINE));
    }

    private Answer unknown(String message) {
        String n = normalize(message);
        StringBuilder sb = new StringBuilder("🤖 Je n'ai pas bien identifié votre demande.");
        if (n.split("\\s+").length <= 2) sb.append(" Pouvez-vous formuler une question plus complète ?");
        sb.append("\n\nExemples :\n• « Combien de vols aujourd'hui ? »\n• « Quels vols sont en retard ? »\n• « Vérifie les anomalies »\n• « Rédige le rapport du jour »\n• « METAR DTTA 071030Z 33012KT 9999 FEW020 22/14 Q1015 »");
        if (!llmEnabled) sb.append("\n\n_Mode moteur métier : configurez OPENAI_API_KEY pour des réponses en langage libre._");
        return new Answer(sb.toString(), "RULES", List.of(SRC_ENGINE));
    }

    // ══════════════════════════════════════════════════════
    // SUGGESTIONS / UTILITAIRES
    // ══════════════════════════════════════════════════════

    private List<String> suggestionsFor(Intent intent, User user) {
        List<String> s = switch (intent) {
            case FLIGHT_STATS -> List.of("Quels vols sont en retard ?", "Compare avec hier", "Vérifie les anomalies");
            case DELAYS -> List.of("Rédige le rapport du jour", "Vols annulés aujourd'hui", "Vérifie les anomalies");
            case CANCELLATIONS, ACTIVE_FLIGHTS -> List.of("Combien de vols aujourd'hui ?", "Quels vols sont en retard ?");
            case ANOMALIES -> List.of("Rédige le rapport du jour", "Quels vols sont en retard ?");
            case REPORT -> List.of("Compare avec hier", "Vérifie les anomalies");
            case FLIGHT_LOOKUP -> List.of("Quels vols sont en retard ?", "Vols en cours");
            case EMERGENCY -> List.of("Rappel séparations ATC", "Codes transpondeur");
            case WEATHER, METAR -> List.of("Rappel turbulence de sillage", "Combien de vols aujourd'hui ?");
            default -> {
                List<String> base = new ArrayList<>(List.of("Combien de vols aujourd'hui ?", "Quels vols sont en retard ?"));
                if (hasRole(user, ERole.ATC) || hasRole(user, ERole.PISTE)) base.add("Vols en cours");
                if (hasRole(user, ERole.SUPERVISEUR) || hasRole(user, ERole.ADMIN)) base.add("Rédige le rapport du jour");
                if (hasRole(user, ERole.QUALITE)) base.add("Vérifie les anomalies");
                if (hasRole(user, ERole.AIS)) base.add("Classe ce document : …");
                if (hasRole(user, ERole.RH_FORMATION)) base.add("Certifications à renouveler");
                base.add("Décoder un METAR");
                yield base.stream().distinct().limit(4).toList();
            }
        };
        return s;
    }

    private List<String> generalSourcesFor(Intent intent) {
        return switch (intent) {
            case EMERGENCY -> List.of(SRC_DOC4444, SRC_ANNEX11);
            case ATC, RUNWAY -> List.of(SRC_DOC4444);
            case WEATHER, METAR -> List.of(SRC_ANNEX3);
            case REGULATION, DOCUMENTS, CLASSIFY -> List.of("OACI Annexe 15");
            default -> List.of();
        };
    }

    private Optional<String> peakHour(FlightSnapshot s) {
        return s.byHour().entrySet().stream().max(Map.Entry.comparingByValue())
                .map(e -> String.format("%02dh–%02dh (%d vols)", e.getKey(), (e.getKey() + 1) % 24, e.getValue()));
    }

    private List<String> extractKeyInfo(String text) {
        List<String> found = new ArrayList<>();
        addMatches(found, text, "\\b\\d{1,2}/\\d{1,2}/\\d{2,4}\\b", "date");
        addMatches(found, text, "\\b\\d{1,2}[h:]\\d{2}\\b", "heure");
        addMatches(found, text, "(?i)\\bpiste\\s*\\d{2}[LRC]?(/\\d{2}[LRC]?)?\\b", null);
        addMatches(found, text, "\\b(DT[A-Z]{2}|LS[A-Z]{2}|LF[A-Z]{2})\\b", "aérodrome");
        addMatches(found, text, "\\b[A-Z]{2,3}\\s?\\d{2,4}\\b", "vol");
        return found.stream().distinct().limit(10).toList();
    }

    private void addMatches(List<String> out, String text, String regex, String label) {
        Matcher m = Pattern.compile(regex).matcher(text);
        while (m.find() && out.size() < 12) out.add(label == null ? m.group() : label + " " + m.group());
    }

    private String stripCommand(String message) {
        int idx = message.indexOf(':');
        return idx > 0 && idx < 60 ? message.substring(idx + 1).trim() : message.replaceFirst("(?i)^\\s*\\S+\\s+", "").trim();
    }

    private String trend(long now, long before) {
        if (before == 0) return now == 0 ? "(=)" : "(nouveau)";
        long pct = Math.round(100.0 * (now - before) / before);
        return pct == 0 ? "(=)" : pct > 0 ? "(▲ +" + pct + " %)" : "(▼ " + pct + " %)";
    }

    private String formatCounts(Map<String, Long> map) {
        return map.entrySet().stream().map(e -> e.getKey() + " (" + e.getValue() + ")").collect(Collectors.joining(", "));
    }

    private String statusLabel(FlightStatus s) {
        if (s == null) return "?";
        return switch (s) {
            case PROGRAMME -> "Programmé"; case EN_APPROCHE -> "En approche"; case ATTERRI -> "Atterri";
            case AU_SOL -> "Au sol"; case EN_EMBARQUEMENT -> "Embarquement"; case DECOLLAGE -> "Décollage";
            case EN_VOL -> "En vol"; case ARRIVE -> "Arrivé"; case RETARDE -> "Retardé"; case ANNULE -> "Annulé";
            case DIVERTE -> "Dérouté";
        };
    }

    private boolean canSeeStaff(User user) {
        return hasRole(user, ERole.ADMIN) || hasRole(user, ERole.SUPERVISEUR) || hasRole(user, ERole.RH_FORMATION);
    }

    private boolean hasRole(User user, ERole role) {
        return user.getRoles().stream().anyMatch(r -> r.getName() == role);
    }

    private List<String> roleNames(User user) {
        return user.getRoles().stream().map(r -> r.getName().name()).sorted().toList();
    }

    static String normalize(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replace('’', '\'').trim();
    }

    private static boolean containsAny(String n, String... keys) {
        for (String k : keys) if (n.contains(k)) return true;
        return false;
    }

    private static boolean startsWithAny(String n, String... keys) {
        for (String k : keys) if (n.startsWith(k)) return true;
        return false;
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static List<String> dedupe(List<String> l) {
        return new ArrayList<>(new LinkedHashSet<>(l));
    }

    private record Answer(String text, String mode, List<String> sources) {}
}
