package com.air.navigation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityDto {

    private Long id;

    /** Titre court de l'événement */
    private String title;

    /** Description détaillée */
    private String description;

    /** success | warning | error | info */
    private String type;

    /** user | flight | system | chat | security */
    private String category;

    /** Nom de l'acteur (utilisateur, système…) */
    private String actor;

    /** Timestamp réel de l'événement */
    private LocalDateTime timestamp;
}
