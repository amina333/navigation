package com.air.navigation.dto.request;

import com.air.navigation.enums.FlightStatus;
import com.air.navigation.enums.FlightType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FlightRequestDto {

    @NotBlank(message = "Le numéro de vol est obligatoire")
    @Pattern(regexp = ValidationPatterns.FLIGHT_NUMBER, message = "Numéro de vol invalide (ex. TU712)")
    private String flightNumber;

    @NotBlank(message = "La compagnie aérienne est obligatoire")
    @Size(min = 2, max = 60, message = "La compagnie doit contenir entre 2 et 60 caractères")
    @Pattern(regexp = ValidationPatterns.SAFE_TEXT, message = "La compagnie contient des caractères non autorisés")
    private String airline;

    @Size(max = 50, message = "Le type d'aéronef ne doit pas dépasser 50 caractères")
    @Pattern(regexp = ValidationPatterns.SAFE_TEXT, message = "Le type d'aéronef contient des caractères non autorisés")
    private String aircraftType;

    @Pattern(regexp = ValidationPatterns.REGISTRATION_OR_EMPTY, message = "Immatriculation invalide (ex. TS-IME)")
    private String aircraftRegistration;

    @NotBlank(message = "L'aéroport de départ est obligatoire")
    @Size(max = 80, message = "L'aéroport de départ ne doit pas dépasser 80 caractères")
    @Pattern(regexp = ValidationPatterns.PLACE_OR_EMPTY, message = "Aéroport de départ invalide")
    private String origin;

    @NotBlank(message = "L'aéroport d'arrivée est obligatoire")
    @Size(max = 80, message = "L'aéroport d'arrivée ne doit pas dépasser 80 caractères")
    @Pattern(regexp = ValidationPatterns.PLACE_OR_EMPTY, message = "Aéroport d'arrivée invalide")
    private String destination;

    @Pattern(regexp = ValidationPatterns.AIRPORT_CODE_OR_EMPTY, message = "Code IATA/OACI de départ invalide")
    private String originIata;

    @Pattern(regexp = ValidationPatterns.AIRPORT_CODE_OR_EMPTY, message = "Code IATA/OACI d'arrivée invalide")
    private String destinationIata;

    @NotNull(message = "L'heure prévue est obligatoire")
    private LocalDateTime scheduledTime;

    private LocalDateTime actualTime;
    private LocalDateTime estimatedTime;

    @Pattern(regexp = ValidationPatterns.RUNWAY_OR_EMPTY, message = "Piste invalide (01 à 36 + L/C/R, ex. 27L)")
    private String runway;

    @Pattern(regexp = ValidationPatterns.GATE_OR_EMPTY, message = "Porte invalide (ex. A12)")
    private String gate;

    @Size(max = 10, message = "Le terminal ne doit pas dépasser 10 caractères")
    private String terminal;

    private FlightStatus status;

    @NotNull(message = "Le type de vol est obligatoire")
    private FlightType flightType;

    @Size(max = 500, message = "Les remarques ne doivent pas dépasser 500 caractères")
    @Pattern(regexp = ValidationPatterns.SAFE_TEXT, message = "Les remarques contiennent des caractères non autorisés")
    private String remarks;

    @Min(value = 0, message = "Le nombre de passagers ne peut pas être négatif")
    @Max(value = 900, message = "Le nombre de passagers est trop élevé")
    private Integer passengersCount;

    @Min(value = 0, message = "Le nombre de membres d'équipage ne peut pas être négatif")
    @Max(value = 60, message = "Le nombre de membres d'équipage est trop élevé")
    private Integer crewCount;

    @JsonIgnore
    @AssertTrue(message = "L'aéroport de départ et l'aéroport d'arrivée doivent être différents")
    public boolean isRouteValid() {
        if (origin == null || destination == null) return true;
        return !origin.trim().equalsIgnoreCase(destination.trim());
    }

    @JsonIgnore
    @AssertTrue(message = "L'heure prévue doit être comprise entre -30 jours et +365 jours")
    public boolean isScheduledTimeReasonable() {
        if (scheduledTime == null) return true;
        LocalDateTime now = LocalDateTime.now();
        return scheduledTime.isAfter(now.minusDays(30)) && scheduledTime.isBefore(now.plusDays(365));
    }
}
