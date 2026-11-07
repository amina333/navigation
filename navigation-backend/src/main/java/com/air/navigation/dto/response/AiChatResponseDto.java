package com.air.navigation.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatResponseDto {

    private Long id;
    private String question;
    /** Réponse (markdown léger : **gras**, listes « • », retours à la ligne) */
    private String response;
    /** LLM ou RULES */
    private String mode;
    private String intent;
    /** Sources identifiées sur lesquelles s'appuie la réponse (§8.8) */
    private List<String> sources;
    /** Questions de relance proposées */
    private List<String> suggestions;
    /** true si la réponse touche un usage sensible → validation humaine requise */
    private boolean requiresHumanValidation;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
}
