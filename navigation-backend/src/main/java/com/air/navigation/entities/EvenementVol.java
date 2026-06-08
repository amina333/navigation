package com.air.navigation.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Section 8.1 UML — Historisation des mouvements de vol (EvenementVol)
 */
@Entity
@Table(name = "evenement_vol")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvenementVol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vol_id", nullable = false)
    private Flight flight;

    @Column(name = "type_evenement", nullable = false)
    private String typeEvenement;   // ex: "CHANGEMENT_STATUT", "CREATION", "MISE_A_JOUR"

    @Column(columnDefinition = "TEXT")
    private String description;     // ex: "Passage de EN_VOL à ARRIVE par ATC_1"

    @Column(name = "date_heure", nullable = false)
    @CreationTimestamp
    private LocalDateTime dateHeure;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auteur_id")
    private User auteur;            // l'utilisateur qui a déclenché l'événement
}
