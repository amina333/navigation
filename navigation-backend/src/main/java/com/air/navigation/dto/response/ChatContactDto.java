package com.air.navigation.dto.response;

import lombok.*;

import java.util.List;

/** Annuaire minimal pour démarrer une conversation privée ou créer un groupe */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatContactDto {
    private Long id;
    private String fullName;
    private String email;
    private String serviceNom;
    private String grade;
    private List<String> roles;
}
