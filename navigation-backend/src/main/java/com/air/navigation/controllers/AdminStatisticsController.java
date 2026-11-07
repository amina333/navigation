package com.air.navigation.controllers;

import com.air.navigation.services.AdminStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Administration", description = "Statistiques globales de la plateforme")
public class AdminStatisticsController {

    private final AdminStatisticsService statisticsService;

    private final com.air.navigation.services.AdminDashboardService dashboardService;

    @GetMapping("/dashboard")
    @Operation(summary = "Tableau de bord administrateur (period = today | week | month)")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERVISEUR')")
    public ResponseEntity<Map<String, Object>> dashboard(@RequestParam(defaultValue = "today") String period) {
        return ResponseEntity.ok(dashboardService.compute(period));
    }

    @GetMapping("/statistics")
    @Operation(summary = "Statistiques : utilisateurs, vols, communications, SkyAI")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERVISEUR')")
    public ResponseEntity<Map<String, Object>> statistics() {
        return ResponseEntity.ok(statisticsService.compute());
    }
}
