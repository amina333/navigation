package com.air.navigation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiChatRequestDto {

    @NotBlank(message = "Veuillez saisir une question.")
    @Size(max = 4000, message = "La question ne doit pas dépasser 4000 caractères.")
    private String message;
}
