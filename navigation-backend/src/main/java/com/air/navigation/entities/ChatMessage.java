package com.air.navigation.entities;

import com.air.navigation.enums.MessageType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false)
    @Builder.Default
    private MessageType messageType = MessageType.TEXT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom chatRoom;

    /** Destinataire pour les DM (null pour les salons) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id")
    private User recipient;

    // ── Pièce jointe (MinIO) ────────────────────────────────────────
    @Column(name = "file_url", columnDefinition = "TEXT")
    private String fileUrl;

    @Column(name = "file_name", length = 500)
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "file_type", length = 100)
    private String fileType;

    // ── Épinglage ───────────────────────────────────────────────────
    @Builder.Default
    @Column(nullable = false)
    private boolean pinned = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pinned_by_id")
    private User pinnedBy;

    @Column(name = "pinned_at")
    private LocalDateTime pinnedAt;

    // ── Suppression logique & édition ───────────────────────────────
    @Builder.Default
    @Column(nullable = false)
    private boolean deleted = false;

    @Column(name = "edited_at")
    private LocalDateTime editedAt;

    // ── Statut lu ───────────────────────────────────────────────────
    @Builder.Default
    @Column(name = "msg_read", nullable = false)
    private boolean msgRead = false;

    // ── Horodatage (audit §8.7) ─────────────────────────────────────
    @CreationTimestamp
    @Column(name = "sent_at", updatable = false)
    private LocalDateTime sentAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
