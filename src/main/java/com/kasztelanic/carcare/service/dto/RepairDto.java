package com.kasztelanic.carcare.service.dto;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Builder
@EqualsAndHashCode(of = { "uuid" })
@ToString(of = { "uuid" })
public class RepairDto implements HasVehicleEvent, HasCost {

    @Getter
    private final Long id;
    @Getter
    private final String uuid;
    @Getter
    private final VehicleEventDto vehicleEvent;
    @Getter
    private final Integer costInCents;
    @Getter
    private final String station;
    @Getter
    private final String details;
}
