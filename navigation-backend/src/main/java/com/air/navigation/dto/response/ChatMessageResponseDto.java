package com.air.navigation.dto.response;

import com.air.navigation.enums.MessageType;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResponseDto {

    private Long id;
    private String content;
    private MessageType messageType;

    // Expéditeur
    private Long senderId;
    private String senderFullName;
    private String senderEmail;

    // Salon
    private Long roomId;
    private String roomName;

    // Pièce jointe
    private String fileUrl;
    private String fileName;
    private Long fileSize;
    private String fileType;

    // Épinglage
    private boolean pinned;
    private String pinnedByFullName;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime pinnedAt;

    // État
    private boolean read;
    private boolean deleted;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime sentAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime editedAt;
}
