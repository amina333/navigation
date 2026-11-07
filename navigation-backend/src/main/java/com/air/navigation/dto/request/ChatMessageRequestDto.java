package com.air.navigation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatMessageRequestDto {

    @NotNull(message = "L'ID du salon est obligatoire")
    private Long roomId;

    @NotBlank(message = "Le contenu du message est obligatoire")
    @Size(max = 4000, message = "Le message ne doit pas dépasser 4000 caractères")
    private String content;
}
