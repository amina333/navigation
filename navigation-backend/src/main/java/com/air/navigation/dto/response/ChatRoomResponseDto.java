package com.air.navigation.dto.response;

import com.air.navigation.enums.RoomType;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoomResponseDto {

    private Long id;
    private String name;
    private String description;
    private RoomType roomType;
    private String serviceCode;
    private boolean privateRoom;

    /** Nom à afficher : nom de l'interlocuteur pour un DM, nom du salon sinon */
    private String displayName;

    /** Pour un DM : l'autre participant */
    private Long otherUserId;
    private String otherUserEmail;

    private List<String> memberNames;
    private List<Long> memberIds;
    private int memberCount;

    private Long createdById;

    /** L'utilisateur courant peut gérer le salon (ajout de membres…) */
    private boolean canManage;

    private String createdByFullName;

    private String lastMessagePreview;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastMessageAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    private long unreadCount;
}
