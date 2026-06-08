package com.air.navigation.services.impl;

import com.air.navigation.dto.request.ChatMessageRequestDto;
import com.air.navigation.dto.request.ChatRoomRequestDto;
import com.air.navigation.dto.response.ChatMessageResponseDto;
import com.air.navigation.dto.response.ChatRoomResponseDto;
import com.air.navigation.entities.ChatMessage;
import com.air.navigation.entities.ChatRoom;
import com.air.navigation.entities.User;
import com.air.navigation.enums.MessageType;
import com.air.navigation.enums.RoomType;
import com.air.navigation.repository.ChatMessageRepository;
import com.air.navigation.repository.ChatRoomRepository;
import com.air.navigation.repository.UserRepository;
import com.air.navigation.services.ChatService;
import com.air.navigation.services.MinioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatServiceImpl implements ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final MinioService minioService;

    // ══════════════════════════════════════════════════════
    // SALONS
    // ══════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public List<ChatRoomResponseDto> getAllPublicRooms() {
        return chatRoomRepository.findAllPublicRooms().stream()
                .map(r -> toRoomDto(r, null))
                .sorted(Comparator.comparing(
                    ChatRoomResponseDto::getLastMessageAt,
                    Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatRoomResponseDto> getRoomsForUser(Long userId) {
        return chatRoomRepository.findRoomsByMemberId(userId).stream()
                .map(r -> toRoomDto(r, userId))
                .sorted(Comparator.comparing(
                    ChatRoomResponseDto::getLastMessageAt,
                    Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ChatRoomResponseDto createRoom(ChatRoomRequestDto dto, Long creatorId) {
        if (chatRoomRepository.existsByName(dto.getName())) {
            throw new RuntimeException("Un salon avec ce nom existe déjà : " + dto.getName());
        }
        User creator = findUserOrThrow(creatorId);
        Set<User> members = new HashSet<>();
        members.add(creator);
        if (dto.getMemberIds() != null) {
            dto.getMemberIds().forEach(mid -> members.add(findUserOrThrow(mid)));
        }
        ChatRoom room = ChatRoom.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .roomType(dto.isPrivateRoom() ? RoomType.GROUP : RoomType.PUBLIC)
                .privateRoom(dto.isPrivateRoom())
                .createdBy(creator)
                .members(members)
                .build();
        return toRoomDto(chatRoomRepository.save(room), creatorId);
    }

    @Override
    @Transactional
    public ChatRoomResponseDto addMemberToRoom(Long roomId, Long userId) {
        ChatRoom room = findRoomOrThrow(roomId);
        room.getMembers().add(findUserOrThrow(userId));
        return toRoomDto(chatRoomRepository.save(room), userId);
    }

    @Override
    @Transactional
    public ChatRoomResponseDto getOrCreateDm(Long userId1, Long userId2) {
        // Cherche un DM existant entre les 2 utilisateurs
        Optional<ChatRoom> existing = chatRoomRepository.findDmBetween(userId1, userId2);
        if (existing.isPresent()) return toRoomDto(existing.get(), userId1);

        User u1 = findUserOrThrow(userId1);
        User u2 = findUserOrThrow(userId2);
        String dmName = "dm-" + Math.min(userId1, userId2) + "-" + Math.max(userId1, userId2);

        ChatRoom dm = ChatRoom.builder()
                .name(dmName)
                .description("Conversation privée entre " + u1.getFullName() + " et " + u2.getFullName())
                .roomType(RoomType.PRIVATE_DM)
                .privateRoom(true)
                .createdBy(u1)
                .members(new HashSet<>(List.of(u1, u2)))
                .build();
        return toRoomDto(chatRoomRepository.save(dm), userId1);
    }

    @Override
    @Transactional
    public ChatRoomResponseDto getOrCreateServiceChannel(String serviceCode, Long creatorId) {
        return chatRoomRepository.findByServiceCode(serviceCode)
                .map(r -> toRoomDto(r, creatorId))
                .orElseGet(() -> {
                    User creator = findUserOrThrow(creatorId);
                    ChatRoom channel = ChatRoom.builder()
                            .name("# " + serviceCode.toLowerCase())
                            .description("Salon officiel du service " + serviceCode)
                            .roomType(RoomType.SERVICE_CHANNEL)
                            .serviceCode(serviceCode)
                            .privateRoom(false)
                            .createdBy(creator)
                            .members(new HashSet<>(List.of(creator)))
                            .build();
                    return toRoomDto(chatRoomRepository.save(channel), creatorId);
                });
    }

    // ══════════════════════════════════════════════════════
    // MESSAGES
    // ══════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageResponseDto> getRoomHistory(Long roomId) {
        return chatMessageRepository.findByRoomIdOrderBySentAtAsc(roomId).stream()
                .filter(m -> !m.isDeleted())
                .map(this::toMessageDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageResponseDto> searchMessages(Long roomId, String query) {
        return chatMessageRepository.searchInRoom(roomId, query).stream()
                .map(this::toMessageDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ChatMessageResponseDto sendMessage(ChatMessageRequestDto dto, Long senderId) {
        User sender = findUserOrThrow(senderId);
        ChatRoom room = findRoomOrThrow(dto.getRoomId());

        ChatMessage message = ChatMessage.builder()
                .content(dto.getContent())
                .messageType(MessageType.TEXT)
                .sender(sender)
                .chatRoom(room)
                .build();

        ChatMessage saved = chatMessageRepository.save(message);

        // Mettre à jour le dernier message du salon
        room.setLastMessagePreview(truncate(dto.getContent(), 80));
        room.setLastMessageAt(LocalDateTime.now());
        chatRoomRepository.save(room);

        ChatMessageResponseDto response = toMessageDto(saved);
        messagingTemplate.convertAndSend("/topic/chat/" + room.getId(), response);
        log.debug("Message envoyé dans '{}' par {}", room.getName(), sender.getEmail());
        return response;
    }

    @Override
    @Transactional
    public ChatMessageResponseDto handleWebSocketMessage(ChatMessageRequestDto dto, String senderEmail) {
        User sender = userRepository.findByEmail(senderEmail)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable: " + senderEmail));
        return sendMessage(dto, sender.getId());
    }

    // ══════════════════════════════════════════════════════
    // PIÈCES JOINTES (MinIO)
    // ══════════════════════════════════════════════════════

    @Override
    @Transactional
    public ChatMessageResponseDto uploadAttachment(Long roomId, MultipartFile file, Long senderId) {
        User sender = findUserOrThrow(senderId);
        ChatRoom room = findRoomOrThrow(roomId);

        String fileUrl = minioService.uploadFile(file, "chat/" + roomId);
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        MessageType msgType = contentType.startsWith("image/") ? MessageType.IMAGE : MessageType.FILE;

        ChatMessage message = ChatMessage.builder()
                .content("[Fichier joint : " + file.getOriginalFilename() + "]")
                .messageType(msgType)
                .sender(sender)
                .chatRoom(room)
                .fileUrl(fileUrl)
                .fileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .fileType(contentType)
                .build();

        ChatMessage saved = chatMessageRepository.save(message);

        // Mise à jour aperçu salon
        room.setLastMessagePreview("📎 " + file.getOriginalFilename());
        room.setLastMessageAt(LocalDateTime.now());
        chatRoomRepository.save(room);

        ChatMessageResponseDto response = toMessageDto(saved);
        messagingTemplate.convertAndSend("/topic/chat/" + room.getId(), response);
        return response;
    }

    // ══════════════════════════════════════════════════════
    // ÉPINGLAGE
    // ══════════════════════════════════════════════════════

    @Override
    @Transactional
    public ChatMessageResponseDto pinMessage(Long messageId, Long userId) {
        ChatMessage msg = findMessageOrThrow(messageId);
        User pinner = findUserOrThrow(userId);
        msg.setPinned(true);
        msg.setPinnedBy(pinner);
        msg.setPinnedAt(LocalDateTime.now());
        ChatMessageResponseDto response = toMessageDto(chatMessageRepository.save(msg));
        messagingTemplate.convertAndSend("/topic/chat/" + msg.getChatRoom().getId() + "/pinned", response);
        log.info("Message {} épinglé par {}", messageId, pinner.getEmail());
        return response;
    }

    @Override
    @Transactional
    public ChatMessageResponseDto unpinMessage(Long messageId, Long userId) {
        ChatMessage msg = findMessageOrThrow(messageId);
        msg.setPinned(false);
        msg.setPinnedBy(null);
        msg.setPinnedAt(null);
        return toMessageDto(chatMessageRepository.save(msg));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageResponseDto> getPinnedMessages(Long roomId) {
        return chatMessageRepository.findPinnedByRoomId(roomId).stream()
                .map(this::toMessageDto).collect(Collectors.toList());
    }

    // ══════════════════════════════════════════════════════
    // UTILITAIRES
    // ══════════════════════════════════════════════════════

    private User findUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable : " + id));
    }

    private ChatRoom findRoomOrThrow(Long id) {
        return chatRoomRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Salon introuvable : " + id));
    }

    private ChatMessage findMessageOrThrow(Long id) {
        return chatMessageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Message introuvable : " + id));
    }

    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "…" : s;
    }

    private ChatRoomResponseDto toRoomDto(ChatRoom room, Long currentUserId) {
        List<String> memberNames = room.getMembers() != null
                ? room.getMembers().stream().map(User::getFullName).collect(Collectors.toList())
                : Collections.emptyList();
        long unread = 0;
        if (currentUserId != null) {
            try {
                unread = chatMessageRepository.countUnreadMessages(room.getId(), currentUserId);
            } catch (Exception e) {
                log.warn("Impossible de compter les messages non-lus pour le salon {} : {}", room.getId(), e.getMessage());
            }
        }
        return ChatRoomResponseDto.builder()
                .id(room.getId())
                .name(room.getName())
                .description(room.getDescription())
                .roomType(room.getRoomType())
                .serviceCode(room.getServiceCode())
                .privateRoom(room.isPrivateRoom())
                .memberNames(memberNames)
                .memberCount(memberNames.size())
                .createdAt(room.getCreatedAt())
                .lastMessagePreview(room.getLastMessagePreview())
                .lastMessageAt(room.getLastMessageAt())
                .createdByFullName(room.getCreatedBy() != null ? room.getCreatedBy().getFullName() : "")
                .unreadCount(unread)
                .build();
    }

    private ChatMessageResponseDto toMessageDto(ChatMessage m) {
        return ChatMessageResponseDto.builder()
                .id(m.getId())
                .content(m.getContent())
                .messageType(m.getMessageType())
                .senderId(m.getSender().getId())
                .senderFullName(m.getSender().getFullName())
                .senderEmail(m.getSender().getEmail())
                .roomId(m.getChatRoom().getId())
                .roomName(m.getChatRoom().getName())
                .fileUrl(m.getFileUrl())
                .fileName(m.getFileName())
                .fileSize(m.getFileSize())
                .fileType(m.getFileType())
                .pinned(m.isPinned())
                .pinnedByFullName(m.getPinnedBy() != null ? m.getPinnedBy().getFullName() : null)
                .pinnedAt(m.getPinnedAt())
                .read(m.isMsgRead())
                .deleted(m.isDeleted())
                .sentAt(m.getSentAt())
                .editedAt(m.getEditedAt())
                .build();
    }
}
