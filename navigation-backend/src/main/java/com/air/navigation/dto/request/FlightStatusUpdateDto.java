package com.air.navigation.dto.request;

import com.air.navigation.enums.FlightStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FlightStatusUpdateDto {

    @NotNull(message = "Le statut est obligatoire")
    private FlightStatus status;

    private String remarks;
    private String runway;
    private String gate;
}
