package com.kasztelanic.carcare.service.dto;

import java.time.LocalDate;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Builder
@EqualsAndHashCode(of = { "id" })
@ToString(of = { "validThru", "vehicleEvent" })
public class InspectionDto implements HasVehicleEvent, HasCost {

    @Getter
    private final Long id;
    @Getter
    private final VehicleEventDto vehicleEvent;
    @Getter
    private final Integer costInCents;
    @Getter
    private final String station;
    @Getter
    private final LocalDate validThru;
    @Getter
    private final String details;
    @Getter
    private final Long vehicleId;
}
