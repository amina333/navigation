package com.air.navigation.services.impl;

import com.air.navigation.dto.request.FlightRequestDto;
import com.air.navigation.dto.request.FlightStatusUpdateDto;
import com.air.navigation.dto.response.FlightResponseDto;
import com.air.navigation.entities.EvenementVol;
import com.air.navigation.entities.Flight;
import com.air.navigation.enums.FlightStatus;
import com.air.navigation.enums.FlightType;
import com.air.navigation.repository.EvenementVolRepository;
import com.air.navigation.repository.FlightRepository;
import com.air.navigation.repository.UserRepository;
import com.air.navigation.services.FlightService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlightServiceImpl implements FlightService {

    private final FlightRepository flightRepository;
    private final EvenementVolRepository evenementVolRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional(readOnly = true)
    public List<FlightResponseDto> getAllFlights() {
        return flightRepository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<FlightResponseDto> getActiveFlights() {
        return flightRepository.findActiveFlights().stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<FlightResponseDto> getTodaysFlights() {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        return flightRepository.findFlightsByDateRange(start, start.plusDays(1)).stream()
                .map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<FlightResponseDto> getFlightsByType(FlightType type) {
        return flightRepository.findByFlightType(type).stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public FlightResponseDto getFlightById(Long id) {
        return toDto(findOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public FlightResponseDto getFlightByNumber(String flightNumber) {
        return toDto(flightRepository.findByFlightNumber(flightNumber)
                .orElseThrow(() -> new RuntimeException("Vol introuvable : " + flightNumber)));
    }

    @Override
    @Transactional
    public FlightResponseDto createFlight(FlightRequestDto dto) {
        if (flightRepository.existsByFlightNumber(dto.getFlightNumber())) {
            throw new RuntimeException("Un vol existe déjà avec le numéro : " + dto.getFlightNumber());
        }
        Flight saved = flightRepository.save(fromDto(dto));
        log.info("Flight created: {}", saved.getFlightNumber());
        broadcast(saved);
        return toDto(saved);
    }

    @Override
    @Transactional
    public FlightResponseDto updateFlight(Long id, FlightRequestDto dto) {
        Flight flight = findOrThrow(id);
        applyDto(flight, dto);
        Flight saved = flightRepository.save(flight);
        broadcast(saved);
        return toDto(saved);
    }

    @Override
    @Transactional
    public FlightResponseDto updateFlightStatus(Long id, FlightStatusUpdateDto dto) {
        Flight flight = findOrThrow(id);
        FlightStatus ancienStatut = flight.getStatus();
        flight.setStatus(dto.getStatus());
        if (dto.getRemarks() != null) flight.setRemarks(dto.getRemarks());
        if (dto.getRunway() != null) flight.setRunway(dto.getRunway());
        if (dto.getGate() != null) flight.setGate(dto.getGate());
        if (dto.getStatus() == FlightStatus.ARRIVE || dto.getStatus() == FlightStatus.DECOLLAGE) {
            flight.setActualTime(LocalDateTime.now());
        }
        Flight saved = flightRepository.save(flight);

        // ── Section 8.1 : Historisation automatique de l'événement ──────────
        EvenementVol evenement = EvenementVol.builder()
                .flight(saved)
                .typeEvenement("CHANGEMENT_STATUT")
                .description(String.format("Passage du statut %s à %s", ancienStatut, dto.getStatus()))
                .build();
        evenementVolRepository.save(evenement);
        // ──────────────────────────────────────────────────────────────

        log.info("Flight {} status {} → {}", saved.getFlightNumber(), ancienStatut, dto.getStatus());
        broadcast(saved);
        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvenementVol> getFlightEvents(Long flightId) {
        return evenementVolRepository.findByFlightIdOrderByDateHeureAsc(flightId);
    }

    @Override
    @Transactional
    public void deleteFlight(Long id) {
        if (!flightRepository.existsById(id)) {
            throw new RuntimeException("Vol introuvable avec l'ID : " + id);
        }
        flightRepository.deleteById(id);
        log.info("Flight deleted: {}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> getFlightStatistics() {
        return Map.of(
                "total", flightRepository.count(),
                "programme", flightRepository.countByStatus(FlightStatus.PROGRAMME),
                "enVol", flightRepository.countByStatus(FlightStatus.EN_VOL),
                "retarde", flightRepository.countByStatus(FlightStatus.RETARDE),
                "annule", flightRepository.countByStatus(FlightStatus.ANNULE),
                "atterri", flightRepository.countByStatus(FlightStatus.ATTERRI)
        );
    }

    private void broadcast(Flight flight) {
        messagingTemplate.convertAndSend("/topic/flights", toDto(flight));
    }

    private Flight findOrThrow(Long id) {
        return flightRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Vol introuvable avec l'ID : " + id));
    }

    private Flight fromDto(FlightRequestDto dto) {
        return Flight.builder()
                .flightNumber(dto.getFlightNumber())
                .airline(dto.getAirline())
                .aircraftType(dto.getAircraftType())
                .aircraftRegistration(dto.getAircraftRegistration())
                .origin(dto.getOrigin())
                .destination(dto.getDestination())
                .originIata(dto.getOriginIata())
                .destinationIata(dto.getDestinationIata())
                .scheduledTime(dto.getScheduledTime())
                .actualTime(dto.getActualTime())
                .estimatedTime(dto.getEstimatedTime())
                .runway(dto.getRunway())
                .gate(dto.getGate())
                .terminal(dto.getTerminal())
                .status(dto.getStatus() != null ? dto.getStatus() : FlightStatus.PROGRAMME)
                .flightType(dto.getFlightType())
                .remarks(dto.getRemarks())
                .passengersCount(dto.getPassengersCount())
                .crewCount(dto.getCrewCount())
                .build();
    }

    private void applyDto(Flight f, FlightRequestDto dto) {
        f.setFlightNumber(dto.getFlightNumber());
        f.setAirline(dto.getAirline());
        f.setAircraftType(dto.getAircraftType());
        f.setAircraftRegistration(dto.getAircraftRegistration());
        f.setOrigin(dto.getOrigin());
        f.setDestination(dto.getDestination());
        f.setOriginIata(dto.getOriginIata());
        f.setDestinationIata(dto.getDestinationIata());
        f.setScheduledTime(dto.getScheduledTime());
        f.setActualTime(dto.getActualTime());
        f.setEstimatedTime(dto.getEstimatedTime());
        f.setRunway(dto.getRunway());
        f.setGate(dto.getGate());
        f.setTerminal(dto.getTerminal());
        if (dto.getStatus() != null) f.setStatus(dto.getStatus());
        f.setFlightType(dto.getFlightType());
        f.setRemarks(dto.getRemarks());
        f.setPassengersCount(dto.getPassengersCount());
        f.setCrewCount(dto.getCrewCount());
    }

    private FlightResponseDto toDto(Flight f) {
        return FlightResponseDto.builder()
                .id(f.getId())
                .flightNumber(f.getFlightNumber())
                .airline(f.getAirline())
                .aircraftType(f.getAircraftType())
                .aircraftRegistration(f.getAircraftRegistration())
                .origin(f.getOrigin())
                .destination(f.getDestination())
                .originIata(f.getOriginIata())
                .destinationIata(f.getDestinationIata())
                .scheduledTime(f.getScheduledTime())
                .actualTime(f.getActualTime())
                .estimatedTime(f.getEstimatedTime())
                .runway(f.getRunway())
                .gate(f.getGate())
                .terminal(f.getTerminal())
                .status(f.getStatus())
                .flightType(f.getFlightType())
                .remarks(f.getRemarks())
                .passengersCount(f.getPassengersCount())
                .crewCount(f.getCrewCount())
                .createdAt(f.getCreatedAt())
                .updatedAt(f.getUpdatedAt())
                .build();
    }
}
