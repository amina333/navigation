package com.air.navigation.services;

import com.air.navigation.dto.request.ChatMessageRequestDto;
import com.air.navigation.dto.request.ChatRoomRequestDto;
import com.air.navigation.dto.response.ChatMessageResponseDto;
import com.air.navigation.dto.response.ChatRoomResponseDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ChatService {

    List<ChatRoomResponseDto> getAllPublicRooms();
    List<ChatRoomResponseDto> getRoomsForUser(Long userId);
    ChatRoomResponseDto createRoom(ChatRoomRequestDto dto, Long creatorId);
    ChatRoomResponseDto addMemberToRoom(Long roomId, Long userId);

    // Messages
    List<ChatMessageResponseDto> getRoomHistory(Long roomId);
    List<ChatMessageResponseDto> searchMessages(Long roomId, String query);
    ChatMessageResponseDto sendMessage(ChatMessageRequestDto dto, Long senderId);
    ChatMessageResponseDto handleWebSocketMessage(ChatMessageRequestDto dto, String senderEmail);

    // Pièces jointes
    ChatMessageResponseDto uploadAttachment(Long roomId, MultipartFile file, Long senderId);

    // Épinglage
    ChatMessageResponseDto pinMessage(Long messageId, Long userId);
    ChatMessageResponseDto unpinMessage(Long messageId, Long userId);
    List<ChatMessageResponseDto> getPinnedMessages(Long roomId);

    // DM privé
    ChatRoomResponseDto getOrCreateDm(Long userId1, Long userId2);

    // Salons de service
    ChatRoomResponseDto getOrCreateServiceChannel(String serviceCode, Long creatorId);
}
