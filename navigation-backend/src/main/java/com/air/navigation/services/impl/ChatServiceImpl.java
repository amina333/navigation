package com.air.navigation.services.impl;

import com.air.navigation.dto.request.ChatMessageRequestDto;
import com.air.navigation.dto.request.ChatRoomRequestDto;
import com.air.navigation.dto.response.ChatContactDto;
import com.air.navigation.dto.response.ChatMessageResponseDto;
import com.air.navigation.dto.response.ChatRoomResponseDto;
import com.air.navigation.entities.ChatMessage;
import com.air.navigation.entities.ChatReadState;
import com.air.navigation.entities.ChatRoom;
import com.air.navigation.entities.User;
import com.air.navigation.enums.ERole;
import com.air.navigation.enums.MessageType;
import com.air.navigation.enums.RoomType;
import com.air.navigation.repository.ChatMessageRepository;
import com.air.navigation.repository.ChatReadStateRepository;
import com.air.navigation.repository.ChatRoomRepository;
import com.air.navigation.repository.UserRepository;
import com.air.navigation.services.ChatService;
import com.air.navigation.services.MinioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implémentation du module chat §8.7.
 *
 * Règles d'accès :
 *  - PUBLIC          : tous les utilisateurs authentifiés
 *  - SERVICE_CHANNEL : agents du service (rôle = serviceCode) + ADMIN / SUPERVISEUR
 *  - PRIVATE_DM      : uniquement les deux participants
 *  - GROUP           : uniquement les membres
 *
 * Temps réel :
 *  - /topic/chat/{roomId}         → nouveaux messages / messages modifiés (épinglé, supprimé)
 *  - /topic/chat/{roomId}/typing  → indicateur de saisie
 *  - /topic/chat/{roomId}/read    → accusés de lecture (DM)
 *  - /user/queue/chat             → événements personnels (nouveau DM, ajout à un groupe…)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatServiceImpl implements ChatService {

    /** Salons officiels par service (cahier des charges §8.7 « salons par service ») */
    public static final Map<String, String> SERVICE_LABELS = new LinkedHashMap<>();
    static {
        SERVICE_LABELS.put("ATC", "Contrôle aérien");
        SERVICE_LABELS.put("PISTE", "Bureau de piste");
        SERVICE_LABELS.put("AIS", "Information aéronautique");
        SERVICE_LABELS.put("SUPERVISEUR", "Supervision");
        SERVICE_LABELS.put("RH_FORMATION", "RH & Formation");
        SERVICE_LABELS.put("QUALITE", "Qualité & Sécurité");
    }

    private static final long MAX_FILE_SIZE = 25L * 1024 * 1024; // 25 Mo
    private static final Set<String> FORBIDDEN_EXTENSIONS = Set.of(
            "exe", "bat", "cmd", "com", "msi", "ps1", "sh", "vbs", "js", "jar", "scr", "dll");
    private static final int MAX_MESSAGE_LENGTH = 4000;
    private static final LocalDateTime EPOCH = LocalDateTime.of(1970, 1, 1, 0, 0);

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatReadStateRepository readStateRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final MinioService minioService;
    private final com.air.navigation.mail.MailService mailService;

    // ══════════════════════════════════════════════════════
    // SALONS
    // ══════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public List<ChatRoomResponseDto> getAllPublicRooms(Long userId) {
        return chatRoomRepository.findAllPublicRooms().stream()
                .map(r -> toRoomDto(r, userId))
                .sorted(roomComparator())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatRoomResponseDto> getRoomsForUser(Long userId) {
        User user = findUserOrThrow(userId);

        Map<Long, ChatRoom> rooms = new LinkedHashMap<>();
        chatRoomRepository.findAllPublicRooms().forEach(r -> rooms.put(r.getId(), r));
        chatRoomRepository.findByRoomType(RoomType.SERVICE_CHANNEL).stream()
                .filter(r -> canAccess(r, user))
                .forEach(r -> rooms.put(r.getId(), r));
        chatRoomRepository.findRoomsByMemberId(userId).forEach(r -> rooms.put(r.getId(), r));

        return rooms.values().stream()
                // Un DM vide n'apparaît que chez celui qui l'a ouvert
                .filter(r -> r.getRoomType() != RoomType.PRIVATE_DM
                        || r.getLastMessageAt() != null
                        || (r.getCreatedBy() != null && r.getCreatedBy().getId().equals(userId)))
                .map(r -> toRoomDto(r, userId))
                .sorted(roomComparator())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ChatRoomResponseDto getRoom(Long roomId, Long userId) {
        User user = findUserOrThrow(userId);
        ChatRoom room = findRoomOrThrow(roomId);
        assertAccess(room, user);
        return toRoomDto(room, userId);
    }

    @Override
    @Transactional
    public ChatRoomResponseDto createRoom(ChatRoomRequestDto dto, Long creatorId) {
        User creator = findUserOrThrow(creatorId);
        String name = dto.getName() == null ? "" : dto.getName().trim();
        if (name.length() < 3 || name.length() > 60) {
            throw new IllegalArgumentException("Le nom doit contenir entre 3 et 60 caractères");
        }
        if (chatRoomRepository.existsByName(name)) {
            throw new IllegalArgumentException("Un salon avec ce nom existe déjà : " + name);
        }

        boolean isGroup = dto.isPrivateRoom();
        if (!isGroup && !isAdminOrSupervisor(creator)) {
            throw new AccessDeniedException("Seuls un administrateur ou un superviseur peuvent créer un salon public");
        }

        Set<User> members = new HashSet<>();
        members.add(creator);
        if (dto.getMemberIds() != null) {
            dto.getMemberIds().stream()
                    .filter(id -> !id.equals(creatorId))
                    .map(this::findUserOrThrow)
                    .filter(User::isActive)
                    .forEach(members::add);
        }
        if (isGroup && members.size() < 2) {
            throw new IllegalArgumentException("Un groupe doit contenir au moins un autre membre");
        }

        ChatRoom room = ChatRoom.builder()
                .name(name)
                .description(dto.getDescription() != null ? dto.getDescription().trim() : null)
                .roomType(isGroup ? RoomType.GROUP : RoomType.PUBLIC)
                .privateRoom(isGroup)
                .createdBy(creator)
                .members(members)
                .build();
        ChatRoom saved = chatRoomRepository.save(room);

        postSystemMessage(saved, creator,
                (isGroup ? "Groupe créé par " : "Salon créé par ") + creator.getFullName());

        if (isGroup) {
            members.stream()
                    .filter(m -> !m.getId().equals(creatorId))
                    .forEach(m -> notifyUser(m, "ROOM_ADDED", saved.getId(), null));
        }
        log.info("[CHAT-AUDIT] {} a créé le salon '{}' ({})", creator.getEmail(), name, saved.getRoomType());
        return toRoomDto(saved, creatorId);
    }

    @Override
    @Transactional
    public ChatRoomResponseDto addMemberToRoom(Long roomId, Long memberId, Long requesterId) {
        ChatRoom room = findRoomOrThrow(roomId);
        User requester = findUserOrThrow(requesterId);
        if (room.getRoomType() != RoomType.GROUP) {
            throw new IllegalArgumentException("On ne peut ajouter des membres qu'à un groupe");
        }
        if (!canManage(room, requester)) {
            throw new AccessDeniedException("Seul le créateur du groupe ou un administrateur peut ajouter des membres");
        }
        User member = findUserOrThrow(memberId);
        if (room.getMembers().stream().anyMatch(u -> u.getId().equals(memberId))) {
            return toRoomDto(room, requesterId);
        }
        room.getMembers().add(member);
        ChatRoom saved = chatRoomRepository.save(room);
        postSystemMessage(saved, requester, requester.getFullName() + " a ajouté " + member.getFullName());
        notifyUser(member, "ROOM_ADDED", saved.getId(), null);
        return toRoomDto(saved, requesterId);
    }

    @Override
    @Transactional
    public void leaveRoom(Long roomId, Long userId) {
        ChatRoom room = findRoomOrThrow(roomId);
        User user = findUserOrThrow(userId);
        if (room.getRoomType() != RoomType.GROUP) {
            throw new IllegalArgumentException("Seuls les groupes peuvent être quittés");
        }
        boolean removed = room.getMembers().removeIf(u -> u.getId().equals(userId));
        if (removed) {
            chatRoomRepository.save(room);
            postSystemMessage(room, user, user.getFullName() + " a quitté le groupe");
        }
    }

    @Override
    @Transactional
    public ChatRoomResponseDto getOrCreateDm(Long userId1, Long userId2) {
        if (Objects.equals(userId1, userId2)) {
            throw new IllegalArgumentException("Impossible d'ouvrir une conversation avec soi-même");
        }
        List<ChatRoom> existing = chatRoomRepository.findDmBetween(userId1, userId2);
        if (!existing.isEmpty()) return toRoomDto(existing.get(0), userId1);

        User u1 = findUserOrThrow(userId1);
        User u2 = findUserOrThrow(userId2);
        if (!u2.isActive()) {
            throw new IllegalArgumentException("Cet utilisateur est désactivé");
        }
        String dmName = "dm-" + Math.min(userId1, userId2) + "-" + Math.max(userId1, userId2);

        ChatRoom dm = ChatRoom.builder()
                .name(dmName)
                .description("Conversation privée")
                .roomType(RoomType.PRIVATE_DM)
                .privateRoom(true)
                .createdBy(u1)
                .members(new HashSet<>(List.of(u1, u2)))
                .build();
        ChatRoom saved = chatRoomRepository.save(dm);
        log.info("[CHAT-AUDIT] DM ouvert entre {} et {}", u1.getEmail(), u2.getEmail());
        return toRoomDto(saved, userId1);
    }

    @Override
    @Transactional
    public ChatRoomResponseDto getOrCreateServiceChannel(String serviceCode, Long creatorId) {
        String code = serviceCode == null ? "" : serviceCode.trim().toUpperCase();
        if (!SERVICE_LABELS.containsKey(code)) {
            throw new IllegalArgumentException("Service inconnu : " + serviceCode);
        }
        User creator = findUserOrThrow(creatorId);
        ChatRoom room = chatRoomRepository.findByServiceCode(code)
                .orElseGet(() -> chatRoomRepository.save(buildServiceChannel(code, creator)));
        assertAccess(room, creator);
        return toRoomDto(room, creatorId);
    }

    /** Utilisé aussi par le ChatDataInitializer */
    public static ChatRoom buildServiceChannel(String code, User creator) {
        return ChatRoom.builder()
                .name("# " + code.toLowerCase())
                .description("Salon officiel — " + SERVICE_LABELS.getOrDefault(code, code))
                .roomType(RoomType.SERVICE_CHANNEL)
                .serviceCode(code)
                .privateRoom(false)
                .createdBy(creator)
                .members(new HashSet<>())
                .build();
    }

    // ══════════════════════════════════════════════════════
    // ANNUAIRE
    // ══════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public List<ChatContactDto> getContacts(Long userId) {
        return userRepository.findAllActiveUsers().stream()
                .filter(u -> !u.getId().equals(userId))
                .sorted(Comparator.comparing(User::getFullName, String.CASE_INSENSITIVE_ORDER))
                .map(u -> ChatContactDto.builder()
                        .id(u.getId())
                        .fullName(u.getFullName())
                        .email(u.getEmail())
                        .serviceNom(u.getServiceNom())
                        .grade(u.getGrade())
                        .roles(roleNames(u))
                        .build())
                .collect(Collectors.toList());
    }

    // ══════════════════════════════════════════════════════
    // MESSAGES
    // ══════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageResponseDto> getRoomHistory(Long roomId, Long userId) {
        ChatRoom room = findRoomOrThrow(roomId);
        assertAccess(room, findUserOrThrow(userId));
        return chatMessageRepository.findByRoomIdOrderBySentAtAsc(roomId).stream()
                .map(this::toMessageDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageResponseDto> searchMessages(Long roomId, String query, Long userId) {
        ChatRoom room = findRoomOrThrow(roomId);
        assertAccess(room, findUserOrThrow(userId));
        String q = query == null ? "" : query.trim();
        if (q.length() < 2) return Collections.emptyList();
        if (q.length() > 100) q = q.substring(0, 100);
        return chatMessageRepository.searchInRoom(roomId, q).stream()
                .map(this::toMessageDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ChatMessageResponseDto sendMessage(ChatMessageRequestDto dto, Long senderId) {
        if (dto == null || dto.getRoomId() == null) {
            throw new IllegalArgumentException("L'ID du salon est obligatoire");
        }
        String content = dto.getContent() == null ? "" : dto.getContent().trim();
        if (content.isEmpty()) {
            throw new IllegalArgumentException("Le message ne peut pas être vide");
        }
        if (content.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Le message ne doit pas dépasser " + MAX_MESSAGE_LENGTH + " caractères");
        }

        User sender = findUserOrThrow(senderId);
        ChatRoom room = findRoomOrThrow(dto.getRoomId());
        assertAccess(room, sender);

        ChatMessage message = ChatMessage.builder()
                .content(content)
                .messageType(MessageType.TEXT)
                .sender(sender)
                .chatRoom(room)
                .recipient(otherMember(room, senderId))
                .build();
        ChatMessage saved = chatMessageRepository.save(message);

        touchRoom(room, truncate(content, 80));
        updateReadState(room, sender);

        ChatMessageResponseDto response = toMessageDto(saved);
        broadcast(room, sender, response);
        log.info("[CHAT-AUDIT] msg#{} {} → salon {} ({})", saved.getId(), sender.getEmail(), room.getId(), room.getRoomType());
        return response;
    }

    @Override
    @Transactional
    public ChatMessageResponseDto handleWebSocketMessage(ChatMessageRequestDto dto, String senderEmail) {
        User sender = userRepository.findByEmail(senderEmail)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable: " + senderEmail));
        return sendMessage(dto, sender.getId());
    }

    @Override
    @Transactional
    public ChatMessageResponseDto deleteMessage(Long messageId, Long userId) {
        ChatMessage msg = findMessageOrThrow(messageId);
        User user = findUserOrThrow(userId);
        boolean isOwner = msg.getSender().getId().equals(userId);
        if (!isOwner && !hasRole(user, ERole.ADMIN)) {
            throw new AccessDeniedException("Vous ne pouvez supprimer que vos propres messages");
        }
        // Suppression logique : l'original reste en base pour la traçabilité (§8.7 journalisation)
        msg.setDeleted(true);
        msg.setPinned(false);
        msg.setEditedAt(LocalDateTime.now());
        ChatMessageResponseDto response = toMessageDto(chatMessageRepository.save(msg));
        messagingTemplate.convertAndSend("/topic/chat/" + msg.getChatRoom().getId(), response);
        log.info("[CHAT-AUDIT] msg#{} supprimé par {}", messageId, user.getEmail());
        return response;
    }

    // ══════════════════════════════════════════════════════
    // LECTURE / NON-LUS
    // ══════════════════════════════════════════════════════

    @Override
    @Transactional
    public void markRoomAsRead(Long roomId, Long userId) {
        ChatRoom room = findRoomOrThrow(roomId);
        User user = findUserOrThrow(userId);
        assertAccess(room, user);
        LocalDateTime now = updateReadState(room, user);

        if (room.getRoomType() == RoomType.PRIVATE_DM) {
            int updated = chatMessageRepository.markReadForRecipient(roomId, userId);
            if (updated > 0) {
                messagingTemplate.convertAndSend("/topic/chat/" + roomId + "/read",
                        Map.of("roomId", roomId, "userId", userId, "readAt", now.toString()));
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public long getTotalUnread(Long userId) {
        return getRoomsForUser(userId).stream().mapToLong(ChatRoomResponseDto::getUnreadCount).sum();
    }

    // ══════════════════════════════════════════════════════
    // PIÈCES JOINTES (MinIO)
    // ══════════════════════════════════════════════════════

    @Override
    @Transactional
    public ChatMessageResponseDto uploadAttachment(Long roomId, MultipartFile file, Long senderId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Le fichier est vide");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Le fichier dépasse la taille maximale autorisée (25 Mo)");
        }
        String originalName = Optional.ofNullable(file.getOriginalFilename()).orElse("fichier");
        String ext = originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase() : "";
        if (FORBIDDEN_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("Type de fichier non autorisé : ." + ext);
        }

        User sender = findUserOrThrow(senderId);
        ChatRoom room = findRoomOrThrow(roomId);
        assertAccess(room, sender);

        String fileUrl = minioService.uploadFile(file, "chat/" + roomId);
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        MessageType msgType = contentType.startsWith("image/") ? MessageType.IMAGE : MessageType.FILE;

        ChatMessage message = ChatMessage.builder()
                .content("[Fichier joint : " + originalName + "]")
                .messageType(msgType)
                .sender(sender)
                .chatRoom(room)
                .recipient(otherMember(room, senderId))
                .fileUrl(fileUrl)
                .fileName(originalName)
                .fileSize(file.getSize())
                .fileType(contentType)
                .build();
        ChatMessage saved = chatMessageRepository.save(message);

        touchRoom(room, "📎 " + originalName);
        updateReadState(room, sender);

        ChatMessageResponseDto response = toMessageDto(saved);
        broadcast(room, sender, response);
        log.info("[CHAT-AUDIT] fichier '{}' envoyé par {} → salon {}", originalName, sender.getEmail(), roomId);
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
        assertAccess(msg.getChatRoom(), pinner);
        if (msg.isDeleted()) throw new IllegalArgumentException("Impossible d'épingler un message supprimé");
        msg.setPinned(true);
        msg.setPinnedBy(pinner);
        msg.setPinnedAt(LocalDateTime.now());
        ChatMessageResponseDto response = toMessageDto(chatMessageRepository.save(msg));
        messagingTemplate.convertAndSend("/topic/chat/" + msg.getChatRoom().getId(), response);
        log.info("[CHAT-AUDIT] msg#{} épinglé par {}", messageId, pinner.getEmail());
        return response;
    }

    @Override
    @Transactional
    public ChatMessageResponseDto unpinMessage(Long messageId, Long userId) {
        ChatMessage msg = findMessageOrThrow(messageId);
        User user = findUserOrThrow(userId);
        assertAccess(msg.getChatRoom(), user);
        msg.setPinned(false);
        msg.setPinnedBy(null);
        msg.setPinnedAt(null);
        ChatMessageResponseDto response = toMessageDto(chatMessageRepository.save(msg));
        messagingTemplate.convertAndSend("/topic/chat/" + msg.getChatRoom().getId(), response);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageResponseDto> getPinnedMessages(Long roomId, Long userId) {
        assertAccess(findRoomOrThrow(roomId), findUserOrThrow(userId));
        return chatMessageRepository.findPinnedByRoomId(roomId).stream()
                .map(this::toMessageDto).collect(Collectors.toList());
    }

    // ══════════════════════════════════════════════════════
    // SÉCURITÉ / TEMPS RÉEL
    // ══════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public boolean canAccessRoom(Long roomId, String userEmail) {
        Optional<User> user = userRepository.findByEmail(userEmail);
        Optional<ChatRoom> room = chatRoomRepository.findById(roomId);
        return user.isPresent() && room.isPresent() && canAccess(room.get(), user.get());
    }

    @Override
    @Transactional(readOnly = true)
    public void broadcastTyping(Long roomId, String userEmail, boolean typing) {
        if (roomId == null) return;
        userRepository.findByEmail(userEmail).ifPresent(u -> {
            if (!canAccessRoom(roomId, userEmail)) return;
            Map<String, Object> payload = new HashMap<>();
            payload.put("roomId", roomId);
            payload.put("userId", u.getId());
            payload.put("userName", u.getFullName());
            payload.put("typing", typing);
            messagingTemplate.convertAndSend("/topic/chat/" + roomId + "/typing", payload);
        });
    }

    private boolean canAccess(ChatRoom room, User user) {
        if (user == null || !user.isActive()) return false;
        return switch (room.getRoomType()) {
            case PUBLIC -> true;
            case SERVICE_CHANNEL -> isAdminOrSupervisor(user)
                    || roleNames(user).contains(room.getServiceCode())
                    || isMember(room, user.getId());
            case PRIVATE_DM, GROUP -> isMember(room, user.getId());
        };
    }

    private void assertAccess(ChatRoom room, User user) {
        if (!canAccess(room, user)) {
            log.warn("[CHAT-AUDIT] accès refusé : {} → salon {}", user.getEmail(), room.getId());
            throw new AccessDeniedException("Vous n'avez pas accès à ce salon");
        }
    }

    private boolean canManage(ChatRoom room, User user) {
        return hasRole(user, ERole.ADMIN)
                || (room.getCreatedBy() != null && room.getCreatedBy().getId().equals(user.getId()));
    }

    /** Diffuse un message : topic du salon + notification personnelle pour DM / groupes */
    private void broadcast(ChatRoom room, User sender, ChatMessageResponseDto response) {
        messagingTemplate.convertAndSend("/topic/chat/" + room.getId(), response);
        if (room.getRoomType() == RoomType.PRIVATE_DM || room.getRoomType() == RoomType.GROUP) {
            boolean isDm = room.getRoomType() == RoomType.PRIVATE_DM;
            String preview = response.getFileName() != null ? "📎 " + response.getFileName() : response.getContent();
            room.getMembers().stream()
                    .filter(m -> !m.getId().equals(sender.getId()))
                    .forEach(m -> {
                        notifyUser(m, "MESSAGE", room.getId(), response);
                        // Préférence « Notifications par e-mail » (activée par défaut)
                        if (m.isActive() && !Boolean.FALSE.equals(m.getEmailNotifications())) {
                            mailService.sendNewMessage(m.getEmail(), m.getFirstName(), sender.getFullName(),
                                    room.getName(), isDm, preview, room.getId());
                        }
                    });
        }
    }

    private void notifyUser(User user, String type, Long roomId, Object payload) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", type);
        event.put("roomId", roomId);
        if (payload != null) event.put("message", payload);
        try {
            messagingTemplate.convertAndSendToUser(user.getEmail(), "/queue/chat", event);
        } catch (Exception e) {
            log.debug("Notification WS impossible pour {} : {}", user.getEmail(), e.getMessage());
        }
    }

    private void postSystemMessage(ChatRoom room, User author, String text) {
        ChatMessage sys = ChatMessage.builder()
                .content(text)
                .messageType(MessageType.SYSTEM)
                .sender(author)
                .chatRoom(room)
                .build();
        ChatMessage saved = chatMessageRepository.save(sys);
        touchRoom(room, text);
        messagingTemplate.convertAndSend("/topic/chat/" + room.getId(), toMessageDto(saved));
    }

    // ══════════════════════════════════════════════════════
    // UTILITAIRES
    // ══════════════════════════════════════════════════════

    private void touchRoom(ChatRoom room, String preview) {
        room.setLastMessagePreview(truncate(preview, 80));
        room.setLastMessageAt(LocalDateTime.now());
        chatRoomRepository.save(room);
    }

    private LocalDateTime updateReadState(ChatRoom room, User user) {
        LocalDateTime now = LocalDateTime.now();
        ChatReadState state = readStateRepository.findByRoomAndUser(room.getId(), user.getId())
                .orElseGet(() -> ChatReadState.builder().room(room).user(user).build());
        state.setLastReadAt(now);
        readStateRepository.save(state);
        return now;
    }

    private User otherMember(ChatRoom room, Long userId) {
        if (room.getRoomType() != RoomType.PRIVATE_DM) return null;
        return room.getMembers().stream().filter(u -> !u.getId().equals(userId)).findFirst().orElse(null);
    }

    private boolean isMember(ChatRoom room, Long userId) {
        return room.getMembers() != null && room.getMembers().stream().anyMatch(u -> u.getId().equals(userId));
    }

    private List<String> roleNames(User user) {
        return user.getRoles().stream().map(r -> r.getName().name()).collect(Collectors.toList());
    }

    private boolean hasRole(User user, ERole role) {
        return user.getRoles().stream().anyMatch(r -> r.getName() == role);
    }

    private boolean isAdminOrSupervisor(User user) {
        return hasRole(user, ERole.ADMIN) || hasRole(user, ERole.SUPERVISEUR);
    }

    private Comparator<ChatRoomResponseDto> roomComparator() {
        return Comparator.comparing(ChatRoomResponseDto::getLastMessageAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ChatRoomResponseDto::getDisplayName, Comparator.nullsLast(String::compareToIgnoreCase));
    }

    private User findUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable : " + id));
    }

    private ChatRoom findRoomOrThrow(Long id) {
        return chatRoomRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Salon introuvable : " + id));
    }

    private ChatMessage findMessageOrThrow(Long id) {
        return chatMessageRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Message introuvable : " + id));
    }

    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "…" : s;
    }

    private ChatRoomResponseDto toRoomDto(ChatRoom room, Long currentUserId) {
        Set<User> members = room.getMembers() != null ? room.getMembers() : Collections.emptySet();
        List<String> memberNames = members.stream().map(User::getFullName).sorted().collect(Collectors.toList());
        List<Long> memberIds = members.stream().map(User::getId).collect(Collectors.toList());

        String displayName = room.getName();
        Long otherUserId = null;
        String otherUserEmail = null;
        if (room.getRoomType() == RoomType.PRIVATE_DM && currentUserId != null) {
            Optional<User> other = members.stream().filter(u -> !u.getId().equals(currentUserId)).findFirst();
            if (other.isPresent()) {
                displayName = other.get().getFullName();
                otherUserId = other.get().getId();
                otherUserEmail = other.get().getEmail();
            }
        } else if (room.getRoomType() == RoomType.SERVICE_CHANNEL) {
            displayName = SERVICE_LABELS.getOrDefault(room.getServiceCode(), room.getName());
        }

        long unread = 0;
        boolean canManage = false;
        if (currentUserId != null) {
            LocalDateTime since = readStateRepository.findByRoomAndUser(room.getId(), currentUserId)
                    .map(ChatReadState::getLastReadAt).orElse(EPOCH);
            unread = chatMessageRepository.countUnreadSince(room.getId(), currentUserId, since);
            canManage = room.getRoomType() == RoomType.GROUP
                    && userRepository.findById(currentUserId).map(u -> canManage(room, u)).orElse(false);
        }

        return ChatRoomResponseDto.builder()
                .id(room.getId())
                .name(room.getName())
                .displayName(displayName)
                .description(room.getDescription())
                .roomType(room.getRoomType())
                .serviceCode(room.getServiceCode())
                .privateRoom(room.isPrivateRoom())
                .otherUserId(otherUserId)
                .otherUserEmail(otherUserEmail)
                .memberNames(memberNames)
                .memberIds(memberIds)
                .memberCount(room.getRoomType() == RoomType.PUBLIC || room.getRoomType() == RoomType.SERVICE_CHANNEL
                        ? 0 : memberNames.size())
                .createdAt(room.getCreatedAt())
                .lastMessagePreview(room.getLastMessagePreview())
                .lastMessageAt(room.getLastMessageAt())
                .createdByFullName(room.getCreatedBy() != null ? room.getCreatedBy().getFullName() : "")
                .createdById(room.getCreatedBy() != null ? room.getCreatedBy().getId() : null)
                .canManage(canManage)
                .unreadCount(unread)
                .build();
    }

    private ChatMessageResponseDto toMessageDto(ChatMessage m) {
        boolean deleted = m.isDeleted();
        return ChatMessageResponseDto.builder()
                .id(m.getId())
                .content(deleted ? "Message supprimé" : m.getContent())
                .messageType(m.getMessageType())
                .senderId(m.getSender().getId())
                .senderFullName(m.getSender().getFullName())
                .senderEmail(m.getSender().getEmail())
                .roomId(m.getChatRoom().getId())
                .roomName(m.getChatRoom().getName())
                .fileUrl(deleted ? null : m.getFileUrl())
                .fileName(deleted ? null : m.getFileName())
                .fileSize(deleted ? null : m.getFileSize())
                .fileType(deleted ? null : m.getFileType())
                .pinned(m.isPinned())
                .pinnedByFullName(m.getPinnedBy() != null ? m.getPinnedBy().getFullName() : null)
                .pinnedAt(m.getPinnedAt())
                .read(m.isMsgRead())
                .deleted(deleted)
                .sentAt(m.getSentAt())
                .editedAt(m.getEditedAt())
                .build();
    }
}
