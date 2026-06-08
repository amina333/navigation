package com.air.navigation.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Set;

@Data
public class ChatRoomRequestDto {

    @NotBlank(message = "Le nom du salon est obligatoire")
    private String name;

    private String description;
    private boolean privateRoom;
    private Set<Long> memberIds;
}
