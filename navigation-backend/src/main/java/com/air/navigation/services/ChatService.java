package com.air.navigation.services;

import com.air.navigation.dto.request.ChatMessageRequestDto;
import com.air.navigation.dto.request.ChatRoomRequestDto;
import com.air.navigation.dto.response.ChatContactDto;
import com.air.navigation.dto.response.ChatMessageResponseDto;
import com.air.navigation.dto.response.ChatRoomResponseDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Module chat interne §8.7 :
 * messagerie instantanée, conversations privées, salons par service, groupes,
 * pièces jointes, notifications temps réel, recherche, épinglage, journalisation.
 */
public interface ChatService {

    // Salons
    List<ChatRoomResponseDto> getAllPublicRooms(Long userId);
    List<ChatRoomResponseDto> getRoomsForUser(Long userId);
    ChatRoomResponseDto getRoom(Long roomId, Long userId);
    ChatRoomResponseDto createRoom(ChatRoomRequestDto dto, Long creatorId);
    ChatRoomResponseDto addMemberToRoom(Long roomId, Long memberId, Long requesterId);
    void leaveRoom(Long roomId, Long userId);

    // Annuaire
    List<ChatContactDto> getContacts(Long userId);

    // Messages
    List<ChatMessageResponseDto> getRoomHistory(Long roomId, Long userId);
    List<ChatMessageResponseDto> searchMessages(Long roomId, String query, Long userId);
    ChatMessageResponseDto sendMessage(ChatMessageRequestDto dto, Long senderId);
    ChatMessageResponseDto handleWebSocketMessage(ChatMessageRequestDto dto, String senderEmail);
    ChatMessageResponseDto deleteMessage(Long messageId, Long userId);

    // Lecture / non-lus
    void markRoomAsRead(Long roomId, Long userId);
    long getTotalUnread(Long userId);

    // Pièces jointes
    ChatMessageResponseDto uploadAttachment(Long roomId, MultipartFile file, Long senderId);

    // Épinglage
    ChatMessageResponseDto pinMessage(Long messageId, Long userId);
    ChatMessageResponseDto unpinMessage(Long messageId, Long userId);
    List<ChatMessageResponseDto> getPinnedMessages(Long roomId, Long userId);

    // DM privé
    ChatRoomResponseDto getOrCreateDm(Long userId1, Long userId2);

    // Salons de service
    ChatRoomResponseDto getOrCreateServiceChannel(String serviceCode, Long creatorId);

    // Sécurité (utilisé aussi par l'intercepteur WebSocket)
    boolean canAccessRoom(Long roomId, String userEmail);

    // Indicateur de saisie
    void broadcastTyping(Long roomId, String userEmail, boolean typing);
}
