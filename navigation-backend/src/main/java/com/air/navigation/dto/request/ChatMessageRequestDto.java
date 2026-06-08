package com.air.navigation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChatMessageRequestDto {

    @NotNull(message = "L'ID du salon est obligatoire")
    private Long roomId;

    @NotBlank(message = "Le contenu du message est obligatoire")
    private String content;
}
