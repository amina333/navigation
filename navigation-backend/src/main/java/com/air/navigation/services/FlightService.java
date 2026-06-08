package com.air.navigation.services;

import com.air.navigation.dto.request.FlightRequestDto;
import com.air.navigation.dto.request.FlightStatusUpdateDto;
import com.air.navigation.dto.response.FlightResponseDto;
import com.air.navigation.entities.EvenementVol;
import com.air.navigation.enums.FlightType;

import java.util.List;
import java.util.Map;

public interface FlightService {
    List<FlightResponseDto> getAllFlights();
    List<FlightResponseDto> getActiveFlights();
    List<FlightResponseDto> getTodaysFlights();
    List<FlightResponseDto> getFlightsByType(FlightType type);
    FlightResponseDto getFlightById(Long id);
    FlightResponseDto getFlightByNumber(String flightNumber);
    FlightResponseDto createFlight(FlightRequestDto dto);
    FlightResponseDto updateFlight(Long id, FlightRequestDto dto);
    FlightResponseDto updateFlightStatus(Long id, FlightStatusUpdateDto dto);
    void deleteFlight(Long id);
    Map<String, Long> getFlightStatistics();
    List<EvenementVol> getFlightEvents(Long flightId);
}
