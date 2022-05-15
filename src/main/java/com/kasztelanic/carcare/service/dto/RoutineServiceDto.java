package com.kasztelanic.carcare.service.dto;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

import java.time.LocalDate;

@Value
@Builder
@EqualsAndHashCode(of = {"id"})
@ToString(of = {"id"}, includeFieldNames = false)
public class RoutineServiceDto implements HasVehicleEvent, HasCost {

    Long id;
    VehicleEventDto vehicleEvent;
    Integer costInCents;
    Integer nextByMileage;
    LocalDate nextByDate;
    String station;
    String details;
    Long vehicleId;
}
