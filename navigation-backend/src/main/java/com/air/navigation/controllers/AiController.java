package com.air.navigation.controllers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * ══════════════════════════════════════════════════════════════
 * SKYAI — Contrôleur Assistant IA de Navigation Aérienne
 * ══════════════════════════════════════════════════════════════
 *
 * ARCHITECTURE DEUX PHASES :
 *
 * ► PHASE 1 (active par défaut) :
 *   Moteur de règles métier aéronautiques. Aucune clé API requise.
 *   Le backend démarre et répond immédiatement.
 *
 * ► PHASE 2 (Spring AI / GPT-4) :
 *   Pour activer l'IA générative réelle :
 *   1. Remplacez la clé dans application.properties :
 *        spring.ai.openai.api-key=sk-VOTRE_VRAIE_CLE
 *   2. Ajoutez spring-ai-openai-spring-boot-starter dans pom.xml
 *      (déjà présent — vérifiez qu'il est téléchargé)
 *   3. Décommentez le constructeur Phase 2 ci-dessous
 *      et commentez le constructeur Phase 1.
 *
 * ══════════════════════════════════════════════════════════════
 */
@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class AiController {

    // ══════════════════════════════════════════════════════════
    // PHASE 2 — Constructeur Spring AI (ACTIF)
    // ══════════════════════════════════════════════════════════
    private final org.springframework.ai.chat.client.ChatClient chatClient;

    public AiController(org.springframework.ai.chat.client.ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder
            .defaultSystem(SYSTEM_PROMPT)
            .build();
        log.info("SkyAI — Démarrage en mode SPRING AI / GPT-4 (Phase 2 — OpenAI activé).");
    }

    private static final String SYSTEM_PROMPT = """
            Tu es SkyAI, un assistant m\u00e9tier pour la navigation a\u00e9rienne.
            R\u00e8gle absolue 1 : Tu ne prends AUCUNE d\u00e9cision critique.
            R\u00e8gle 2 : Tes r\u00e9ponses doivent \u00eatre concises et professionnelles.
            R\u00e8gle 3 : Rappelle toujours \u00e0 l'agent humain de v\u00e9rifier les radars pour les d\u00e9cisions en temps r\u00e9el.
            """;

    // ══════════════════════════════════════════════════════════
    // ENDPOINT PRINCIPAL
    // ══════════════════════════════════════════════════════════

    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(@RequestBody Map<String, String> request) {
        String message = request.getOrDefault("message", "").trim();

        if (message.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("response", "Veuillez saisir un message."));
        }

        log.debug("SkyAI — Message reçu : {}", message);

        // Phase 2 — Spring AI / GPT-4 (ACTIF)
        try {
            String aiResponse = chatClient.prompt()
                .user(message)
                .call()
                .content();
            log.debug("SkyAI — Réponse GPT-4 : {}", aiResponse);
            return ResponseEntity.ok(Map.of("response", aiResponse));
        } catch (Exception e) {
            log.error("SkyAI — Erreur Spring AI, fallback règles métier : {}", e.getMessage());
        }

        // Fallback Phase 1 : Moteur de règles métier
        String response = generateRuleBasedResponse(message.toLowerCase());
        return ResponseEntity.ok(Map.of("response", response));
    }

    // ══════════════════════════════════════════════════════════
    // MOTEUR DE RÈGLES MÉTIER AÉRONAUTIQUES
    // ══════════════════════════════════════════════════════════

    private String generateRuleBasedResponse(String msg) {

        // Accueil
        if (contains(msg, "bonjour", "salut", "hello", "bonsoir", "hi")) {
            return "✈️ Bonjour ! Je suis **SkyAI**, votre assistant de navigation aérienne. " +
                   "Je peux vous aider avec les procédures ATC, statuts de vols, météo, urgences ou réglementations OACI. " +
                   "Comment puis-je vous assister ?";
        }

        // Aide
        if (contains(msg, "aide", "help", "assistance", "que sais-tu", "capabilities")) {
            return "💡 **Domaines d'assistance SkyAI :**\n" +
                   "• 🗼 Procédures ATC & phraséologie OACI\n" +
                   "• ✈️ Statuts et gestion des vols\n" +
                   "• 🌤️ Météo : METAR, TAF, ATIS\n" +
                   "• 🚨 Urgences : MAYDAY, PAN PAN, SSLIA\n" +
                   "• 📋 Réglementations OACI / DGAC\n" +
                   "• 🛬 Coordination piste & SOL\n\nPosez votre question !";
        }

        // Météo
        if (contains(msg, "météo", "meteo", "vent", "visibilité", "metar", "taf", "atis", "nuage", "brouillard")) {
            return "🌤️ **Informations météorologiques :**\n" +
                   "• **METAR** : Observation en temps réel (décodez via OGIMET ou aviationweather.gov)\n" +
                   "• **TAF** : Prévision sur 24-30h pour l'aéroport\n" +
                   "• **ATIS** : Diffusion automatique locale (fréquence spécifique à chaque aéroport)\n" +
                   "• **VMC** : Visibilité > 5 km, plafond > 1500 ft\n" +
                   "• **IMC** : Déclenche procédures IFR, augmente les séparations ATC\n" +
                   "• **Cisaillement** : Signaler immédiatement au superviseur et aux équipages";
        }

        // Urgences
        if (contains(msg, "urgence", "mayday", "pan pan", "détresse", "secours", "incendie", "evacu")) {
            return "🚨 **PROCÉDURE D'URGENCE OACI :**\n\n" +
                   "**MAYDAY (détresse absolue) :**\n" +
                   "• Émettre sur 121.5 MHz : *MAYDAY MAYDAY MAYDAY [indicatif] [position] [nature] [altitude] [intentions]*\n" +
                   "• ATC : Priorité absolue, dégager l'espace aérien adjacent\n" +
                   "• Alerter immédiatement SSLIA (pompiers aéroport)\n" +
                   "• Notifier le superviseur et l'autorité ATC supérieure\n" +
                   "• Préparer la piste la plus courte/disponible\n\n" +
                   "**PAN PAN (urgence, non-détresse) :** Même procédure, priorité légèrement moindre.\n\n" +
                   "⚠️ Tout incident doit être consigné dans le registre ATC.";
        }

        // Pistes
        if (contains(msg, "piste", "runway", "décollage", "atterrissage", "qfu", "qnh", "toucher")) {
            return "🛬 **Gestion des pistes :**\n" +
                   "• **Sélection QFU** : Piste face au vent (vent arrière max 5 kt en conditions normales)\n" +
                   "• **Wake turbulence** : Heavy→Medium ≥ 3 NM / 3 min ; Heavy→Light ≥ 4 NM / 4 min\n" +
                   "• **Décollage** : Clairance explicite + read-back obligatoire avant entrée piste\n" +
                   "• **Atterrissage** : Confirmer piste libre visuellement et par radar avant autorisation\n" +
                   "• **NOTAM** : Vérifiez les NOTAM actifs (fermetures, travaux, obstacles)";
        }

        // Vols et statuts
        if (contains(msg, "vol", "statut", "retard", "annul", "programme", "flight", "arrivée", "départ")) {
            return "✈️ **Gestion des vols :**\n" +
                   "Consultez le **tableau de bord des Vols** pour les statuts en temps réel (WebSocket).\n\n" +
                   "**Statuts disponibles :**\n" +
                   "🔵 PROGRAMMÉ → 🟡 EN EMBARQUEMENT → 🟢 EN VOL\n" +
                   "🟣 EN APPROCHE → ✅ ATTERRI → 🔴 RETARDÉ / ❌ ANNULÉ\n\n" +
                   "Pour modifier un statut : bouton ✏️ dans le tableau (rôle ATC, PISTE ou SUPERVISEUR requis).";
        }

        // ATC
        if (contains(msg, "atc", "clairance", "clearance", "contrôle", "séparation", "radar", "transponder", "squawk")) {
            return "🗼 **Procédures ATC :**\n" +
                   "• **Séparations minimales** : 1000 ft vertical OU 5 NM horizontal (en route) / 3 NM (approche)\n" +
                   "• **Read-back obligatoire** pour : clairances de piste, caps, altitudes, fréquences, squawk\n" +
                   "• **Phraséologie** : Anglais OACI pour vols internationaux, français autorisé localement\n" +
                   "• **Transfert de contrôle** : Coordination secteur adjacent 5 min avant la limite\n" +
                   "• **TCAS RA** : Le pilote suit la RA, ATC informé immédiatement — ne pas contredire la RA";
        }

        // Réglementations
        if (contains(msg, "règlement", "oaci", "icao", "dgac", "enac", "annexe", "annex", "sera", "aip")) {
            return "📋 **Réglementations aéronautiques de référence :**\n" +
                   "• **OACI Annexe 2** : Règles de l'air\n" +
                   "• **OACI Annexe 11** : Services de la circulation aérienne (ATS)\n" +
                   "• **OACI Doc 4444** (PANS-ATM) : Procédures pour les services ATC\n" +
                   "• **OACI Doc 9432** : Manuel de phraséologie radiotéléphonique\n" +
                   "• **SERA** : Règles de l'air européennes (EASA)\n" +
                   "• **AIP Tunisie** (DTAC) : Réglementation locale\n\n" +
                   "Source officielle : icao.int — Publications Navigation — PANS & Annexes";
        }

        // Réponse par défaut
        return "🤖 Je n'ai pas de réponse spécifique à cette demande.\n\n" +
               "Je peux vous aider sur : **météo, urgences, pistes, ATC, vols, réglementations OACI**.\n" +
               "Reformulez votre question ou tapez **aide** pour voir tous mes domaines d'expertise.";
    }

    /** Utilitaire : vérifie si le message contient au moins un des mots-clés. */
    private boolean contains(String msg, String... keywords) {
        for (String kw : keywords) {
            if (msg.contains(kw)) return true;
        }
        return false;
    }
}
