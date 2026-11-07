package com.air.navigation.repository;

import com.air.navigation.entities.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("SELECT m FROM ChatMessage m WHERE m.chatRoom.id = :roomId ORDER BY m.sentAt ASC")
    List<ChatMessage> findByRoomIdOrderBySentAtAsc(@Param("roomId") Long roomId);

    /** Non-lus pour un utilisateur : messages des autres envoyés après sa dernière lecture */
    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.chatRoom.id = :roomId AND m.deleted = false " +
           "AND m.sender.id <> :userId AND m.sentAt > :since")
    long countUnreadSince(@Param("roomId") Long roomId,
                          @Param("userId") Long userId,
                          @Param("since") LocalDateTime since);

    /** Accusé de lecture DM : marque comme lus les messages reçus par l'utilisateur */
    @Modifying
    @Query("UPDATE ChatMessage m SET m.msgRead = true WHERE m.chatRoom.id = :roomId " +
           "AND m.sender.id <> :userId AND m.msgRead = false")
    int markReadForRecipient(@Param("roomId") Long roomId, @Param("userId") Long userId);

    /** Recherche dans l'historique (§8.7) — contenu ou nom de fichier */
    @Query("SELECT m FROM ChatMessage m WHERE m.chatRoom.id = :roomId AND m.deleted = false " +
           "AND (LOWER(m.content) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(COALESCE(m.fileName, '')) LIKE LOWER(CONCAT('%', :query, '%'))) ORDER BY m.sentAt DESC")
    List<ChatMessage> searchInRoom(@Param("roomId") Long roomId, @Param("query") String query);

    /** Messages épinglés d'un salon */
    @Query("SELECT m FROM ChatMessage m WHERE m.chatRoom.id = :roomId AND m.pinned = true AND m.deleted = false ORDER BY m.pinnedAt DESC")
    List<ChatMessage> findPinnedByRoomId(@Param("roomId") Long roomId);
}
