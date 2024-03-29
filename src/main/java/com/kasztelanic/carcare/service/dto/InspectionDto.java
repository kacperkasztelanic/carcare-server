package com.kasztelanic.carcare.service.dto;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

import java.time.LocalDate;

@Value
@Builder
@EqualsAndHashCode(of = {"id"})
@ToString(of = {"validThru", "vehicleEvent"})
public class InspectionDto implements HasVehicleEvent, HasCost {

    Long id;
    VehicleEventDto vehicleEvent;
    Integer costInCents;
    String station;
    LocalDate validThru;
    String details;
    Long vehicleId;
}
