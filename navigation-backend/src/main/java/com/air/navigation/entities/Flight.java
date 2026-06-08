package com.air.navigation.entities;

import com.air.navigation.enums.FlightStatus;
import com.air.navigation.enums.FlightType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "flights")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Flight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "flight_number", nullable = false, unique = true)
    private String flightNumber;

    @Column(nullable = false)
    private String airline;

    @Column(name = "aircraft_type")
    private String aircraftType;

    @Column(name = "aircraft_registration")
    private String aircraftRegistration;

    private String origin;
    private String destination;

    @Column(name = "origin_iata", length = 4)
    private String originIata;

    @Column(name = "destination_iata", length = 4)
    private String destinationIata;

    @Column(name = "scheduled_time", nullable = false)
    private LocalDateTime scheduledTime;

    @Column(name = "actual_time")
    private LocalDateTime actualTime;

    @Column(name = "estimated_time")
    private LocalDateTime estimatedTime;

    private String runway;
    private String gate;
    private String terminal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private FlightStatus status = FlightStatus.PROGRAMME;

    @Enumerated(EnumType.STRING)
    @Column(name = "flight_type", nullable = false)
    private FlightType flightType;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "passengers_count")
    private Integer passengersCount;

    @Column(name = "crew_count")
    private Integer crewCount;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
