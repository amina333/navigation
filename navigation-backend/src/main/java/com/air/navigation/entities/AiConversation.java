package com.air.navigation.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Journal des échanges avec SkyAI (§8.8 « les résultats doivent rester traçables »,
 * §12 entités « conversations IA » et « références IA »).
 */
@Entity
@Table(name = "ai_conversations", indexes = @Index(name = "idx_ai_conv_user", columnList = "user_id, created_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String question;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String answer;

    /** LLM (Spring AI / OpenAI) ou RULES (moteur métier interne) */
    @Column(length = 20, nullable = false)
    private String mode;

    /** Intention détectée (FLIGHT_STATS, DELAYS, EMERGENCY…) */
    @Column(length = 40)
    private String intent;

    /** Sources citées, séparées par « | » */
    @Column(columnDefinition = "TEXT")
    private String sources;

    /** Masqué de l'historique par l'utilisateur (conservé pour l'audit) */
    @Builder.Default
    @Column(nullable = false)
    private boolean hidden = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
