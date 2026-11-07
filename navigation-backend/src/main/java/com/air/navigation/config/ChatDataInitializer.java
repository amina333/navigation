package com.air.navigation.config;

import com.air.navigation.entities.ChatRoom;
import com.air.navigation.entities.User;
import com.air.navigation.enums.ERole;
import com.air.navigation.enums.RoomType;
import com.air.navigation.repository.ChatRoomRepository;
import com.air.navigation.repository.UserRepository;
import com.air.navigation.services.impl.ChatServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;

/**
 * Crée au démarrage les salons de base du module chat §8.7 :
 *  - un salon public « # général »
 *  - un salon officiel par service (ATC, PISTE, AIS, SUPERVISEUR, RH_FORMATION, QUALITE)
 * Idempotent : ne recrée rien s'il existe déjà.
 */
@Component
@Order(10)
@RequiredArgsConstructor
@Slf4j
public class ChatDataInitializer implements CommandLineRunner {

    private static final String GENERAL_ROOM = "# général";

    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void run(String... args) {
        User admin = userRepository.findByRoleName(ERole.ADMIN).stream().findFirst().orElse(null);

        if (!chatRoomRepository.existsByName(GENERAL_ROOM)) {
            chatRoomRepository.save(ChatRoom.builder()
                    .name(GENERAL_ROOM)
                    .description("Salon public — annonces et échanges entre tous les agents")
                    .roomType(RoomType.PUBLIC)
                    .privateRoom(false)
                    .createdBy(admin)
                    .members(new HashSet<>())
                    .build());
            log.info("Chat — salon public '{}' créé", GENERAL_ROOM);
        }

        ChatServiceImpl.SERVICE_LABELS.keySet().forEach(code -> {
            if (chatRoomRepository.findByServiceCode(code).isEmpty()) {
                chatRoomRepository.save(ChatServiceImpl.buildServiceChannel(code, admin));
                log.info("Chat — salon de service '{}' créé", code);
            }
        });
    }
}
