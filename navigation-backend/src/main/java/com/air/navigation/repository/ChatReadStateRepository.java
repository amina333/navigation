package com.air.navigation.repository;

import com.air.navigation.entities.ChatReadState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChatReadStateRepository extends JpaRepository<ChatReadState, Long> {

    @Query("SELECT s FROM ChatReadState s WHERE s.room.id = :roomId AND s.user.id = :userId")
    Optional<ChatReadState> findByRoomAndUser(@Param("roomId") Long roomId, @Param("userId") Long userId);
}
