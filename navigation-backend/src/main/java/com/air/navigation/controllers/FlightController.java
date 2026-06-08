package com.air.navigation.controllers;

import com.air.navigation.dto.request.FlightRequestDto;
import com.air.navigation.dto.request.FlightStatusUpdateDto;
import com.air.navigation.dto.response.FlightResponseDto;
import com.air.navigation.entities.EvenementVol;
import com.air.navigation.enums.FlightType;
import com.air.navigation.services.FlightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/flights")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Vols", description = "Tableau de bord et gestion des vols")
public class FlightController {

    private final FlightService flightService;

    @GetMapping
    @Operation(summary = "Liste de tous les vols")
    public ResponseEntity<List<FlightResponseDto>> getAllFlights() {
        return ResponseEntity.ok(flightService.getAllFlights());
    }

    @GetMapping("/active")
    @Operation(summary = "Vols actifs")
    public ResponseEntity<List<FlightResponseDto>> getActiveFlights() {
        return ResponseEntity.ok(flightService.getActiveFlights());
    }

    @GetMapping("/today")
    @Operation(summary = "Vols du jour")
    public ResponseEntity<List<FlightResponseDto>> getTodaysFlights() {
        return ResponseEntity.ok(flightService.getTodaysFlights());
    }

    @GetMapping("/type/{type}")
    @Operation(summary = "Vols par type (ARRIVEE / DEPART)")
    public ResponseEntity<List<FlightResponseDto>> getFlightsByType(@PathVariable FlightType type) {
        return ResponseEntity.ok(flightService.getFlightsByType(type));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'un vol par ID")
    public ResponseEntity<FlightResponseDto> getFlightById(@PathVariable Long id) {
        return ResponseEntity.ok(flightService.getFlightById(id));
    }

    @GetMapping("/number/{flightNumber}")
    @Operation(summary = "Rechercher un vol par numéro")
    public ResponseEntity<FlightResponseDto> getFlightByNumber(@PathVariable String flightNumber) {
        return ResponseEntity.ok(flightService.getFlightByNumber(flightNumber));
    }

    @GetMapping("/statistics")
    @Operation(summary = "Statistiques des vols pour le dashboard")
    public ResponseEntity<Map<String, Long>> getStatistics() {
        return ResponseEntity.ok(flightService.getFlightStatistics());
    }

    @PostMapping
    @Operation(summary = "Créer un nouveau vol")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERVISEUR')")
    public ResponseEntity<FlightResponseDto> createFlight(@Valid @RequestBody FlightRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(flightService.createFlight(dto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Mettre à jour un vol")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERVISEUR')")
    public ResponseEntity<FlightResponseDto> updateFlight(@PathVariable Long id,
                                                          @Valid @RequestBody FlightRequestDto dto) {
        return ResponseEntity.ok(flightService.updateFlight(id, dto));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Mettre à jour le statut d'un vol")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERVISEUR', 'ATC', 'PISTE')")
    public ResponseEntity<FlightResponseDto> updateStatus(@PathVariable Long id,
                                                          @Valid @RequestBody FlightStatusUpdateDto dto) {
        return ResponseEntity.ok(flightService.updateFlightStatus(id, dto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un vol")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Void> deleteFlight(@PathVariable Long id) {
        flightService.deleteFlight(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/events")
    @Operation(summary = "Historique des événements d'un vol (Section 8.1 UML)")
    public ResponseEntity<List<EvenementVol>> getFlightEvents(@PathVariable Long id) {
        return ResponseEntity.ok(flightService.getFlightEvents(id));
    }
}
