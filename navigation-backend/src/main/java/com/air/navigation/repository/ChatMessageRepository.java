package com.air.navigation.repository;

import com.air.navigation.entities.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("SELECT m FROM ChatMessage m WHERE m.chatRoom.id = :roomId ORDER BY m.sentAt ASC")
    List<ChatMessage> findByRoomIdOrderBySentAtAsc(@Param("roomId") Long roomId);

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.chatRoom.id = :roomId AND m.msgRead = false AND m.sender.id != :userId")
    long countUnreadMessages(@Param("roomId") Long roomId, @Param("userId") Long userId);

    /** Recherche dans l'historique (§8.7) */
    @Query("SELECT m FROM ChatMessage m WHERE m.chatRoom.id = :roomId AND m.deleted = false " +
           "AND LOWER(m.content) LIKE LOWER(CONCAT('%', :query, '%')) ORDER BY m.sentAt DESC")
    List<ChatMessage> searchInRoom(@Param("roomId") Long roomId, @Param("query") String query);

    /** Messages épinglés d'un salon */
    @Query("SELECT m FROM ChatMessage m WHERE m.chatRoom.id = :roomId AND m.pinned = true AND m.deleted = false ORDER BY m.pinnedAt DESC")
    List<ChatMessage> findPinnedByRoomId(@Param("roomId") Long roomId);
}
