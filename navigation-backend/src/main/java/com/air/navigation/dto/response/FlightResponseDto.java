package com.air.navigation.dto.response;

import com.air.navigation.enums.FlightStatus;
import com.air.navigation.enums.FlightType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FlightResponseDto {
    private Long id;
    private String flightNumber;
    private String airline;
    private String aircraftType;
    private String aircraftRegistration;
    private String origin;
    private String destination;
    private String originIata;
    private String destinationIata;
    private LocalDateTime scheduledTime;
    private LocalDateTime actualTime;
    private LocalDateTime estimatedTime;
    private String runway;
    private String gate;
    private String terminal;
    private FlightStatus status;
    private FlightType flightType;
    private String remarks;
    private Integer passengersCount;
    private Integer crewCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
