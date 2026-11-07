package com.air.navigation.dto.request;

import com.air.navigation.enums.FlightStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FlightStatusUpdateDto {

    @NotNull(message = "Le statut est obligatoire")
    private FlightStatus status;

    @jakarta.validation.constraints.Size(max = 500, message = "Les remarques ne doivent pas dépasser 500 caractères")
    @jakarta.validation.constraints.Pattern(regexp = ValidationPatterns.SAFE_TEXT, message = "Les remarques contiennent des caractères non autorisés")
    private String remarks;

    @jakarta.validation.constraints.Pattern(regexp = ValidationPatterns.RUNWAY_OR_EMPTY, message = "Piste invalide (01 à 36 + L/C/R, ex. 27L)")
    private String runway;

    @jakarta.validation.constraints.Pattern(regexp = ValidationPatterns.GATE_OR_EMPTY, message = "Porte invalide (ex. A12)")
    private String gate;
}
