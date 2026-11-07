package com.air.navigation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

@Data
public class ChatRoomRequestDto {

    @NotBlank(message = "Le nom du salon est obligatoire")
    @Size(min = 3, max = 60, message = "Le nom doit contenir entre 3 et 60 caractères")
    @Pattern(regexp = "^[\\p{L}0-9 _#'.\\-]+$",
             message = "Le nom contient des caractères non autorisés")
    private String name;

    @Size(max = 255, message = "La description ne doit pas dépasser 255 caractères")
    private String description;

    /** true = groupe privé (membres choisis), false = salon public */
    private boolean privateRoom;

    @Size(max = 50, message = "Un groupe ne peut pas dépasser 50 membres")
    private Set<Long> memberIds;
}
