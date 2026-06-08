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

    private List<String> memberNames;
    private int memberCount;

    private String createdByFullName;

    private String lastMessagePreview;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastMessageAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    private long unreadCount;
}
