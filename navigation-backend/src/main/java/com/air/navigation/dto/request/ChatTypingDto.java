package com.air.navigation.dto.request;

import lombok.Data;

@Data
public class ChatTypingDto {
    private Long roomId;
    private boolean typing;
}
