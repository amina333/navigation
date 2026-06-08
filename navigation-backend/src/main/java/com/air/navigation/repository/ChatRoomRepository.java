package com.air.navigation.repository;

import com.air.navigation.entities.ChatRoom;
import com.air.navigation.enums.RoomType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    /** Salons dont l'utilisateur est membre */
    @Query("SELECT r FROM ChatRoom r JOIN r.members m WHERE m.id = :userId")
    List<ChatRoom> findRoomsByMemberId(@Param("userId") Long userId);

    /** Tous les salons publics */
    @Query("SELECT r FROM ChatRoom r WHERE r.privateRoom = false")
    List<ChatRoom> findAllPublicRooms();

    boolean existsByName(String name);

    /** Trouver un DM existant entre deux utilisateurs */
    @Query("SELECT r FROM ChatRoom r JOIN r.members m1 JOIN r.members m2 " +
           "WHERE r.roomType = 'PRIVATE_DM' AND m1.id = :uid1 AND m2.id = :uid2")
    Optional<ChatRoom> findDmBetween(@Param("uid1") Long uid1, @Param("uid2") Long uid2);

    /** Trouver un salon de service par code */
    Optional<ChatRoom> findByServiceCode(String serviceCode);

    /** Tous les salons de service */
    List<ChatRoom> findByRoomType(RoomType roomType);
}
