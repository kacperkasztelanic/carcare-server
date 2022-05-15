package com.kasztelanic.carcare.service.dto;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

@Value
@Builder
@EqualsAndHashCode(of = {"id"})
@ToString(of = {"id"})
public class RepairDto implements HasVehicleEvent, HasCost {

    Long id;
    VehicleEventDto vehicleEvent;
    Integer costInCents;
    String station;
    String details;
    Long vehicleId;
}
