package com.air.navigation.repository;

import com.air.navigation.entities.AiConversation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiConversationRepository extends JpaRepository<AiConversation, Long> {

    @Query("SELECT c FROM AiConversation c WHERE c.user.id = :userId AND c.hidden = false ORDER BY c.createdAt DESC")
    List<AiConversation> findRecentVisible(@Param("userId") Long userId, Pageable pageable);

    /** L'historique est masqué côté utilisateur mais conservé pour la traçabilité */
    @Modifying
    @Query("UPDATE AiConversation c SET c.hidden = true WHERE c.user.id = :userId AND c.hidden = false")
    int hideAllForUser(@Param("userId") Long userId);
}
