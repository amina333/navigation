package com.air.navigation.repository;

import com.air.navigation.entities.Flight;
import com.air.navigation.enums.FlightStatus;
import com.air.navigation.enums.FlightType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FlightRepository extends JpaRepository<Flight, Long> {

    Optional<Flight> findByFlightNumber(String flightNumber);

    boolean existsByFlightNumber(String flightNumber);

    List<Flight> findByFlightType(FlightType flightType);

    List<Flight> findByStatus(FlightStatus status);

    List<Flight> findByAirline(String airline);

    @Query("SELECT f FROM Flight f WHERE f.scheduledTime BETWEEN :start AND :end ORDER BY f.scheduledTime ASC")
    List<Flight> findFlightsByDateRange(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT f FROM Flight f WHERE f.flightType = :type AND f.scheduledTime BETWEEN :start AND :end ORDER BY f.scheduledTime ASC")
    List<Flight> findByTypeAndDateRange(
            @Param("type") FlightType type,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT f FROM Flight f WHERE f.status NOT IN ('ARRIVE', 'ANNULE') ORDER BY f.scheduledTime ASC")
    List<Flight> findActiveFlights();

    @Query("SELECT COUNT(f) FROM Flight f WHERE f.status = :status")
    long countByStatus(@Param("status") FlightStatus status);
}
