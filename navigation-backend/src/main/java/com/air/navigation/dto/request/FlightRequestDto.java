package com.air.navigation.dto.request;

import com.air.navigation.enums.FlightStatus;
import com.air.navigation.enums.FlightType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FlightRequestDto {

    @NotBlank(message = "Le numéro de vol est obligatoire")
    private String flightNumber;

    @NotBlank(message = "La compagnie aérienne est obligatoire")
    private String airline;

    private String aircraftType;
    private String aircraftRegistration;
    private String origin;
    private String destination;
    private String originIata;
    private String destinationIata;

    @NotNull(message = "L'heure prévue est obligatoire")
    private LocalDateTime scheduledTime;

    private LocalDateTime actualTime;
    private LocalDateTime estimatedTime;

    private String runway;
    private String gate;
    private String terminal;

    private FlightStatus status;

    @NotNull(message = "Le type de vol est obligatoire")
    private FlightType flightType;

    private String remarks;
    private Integer passengersCount;
    private Integer crewCount;
}
