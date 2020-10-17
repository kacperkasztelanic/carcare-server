package com.kasztelanic.carcare.service.dto;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

@Value
@Builder
@EqualsAndHashCode(of = { "id" })
@ToString(of = { "costInCents", "volume" }, includeFieldNames = false)
public class RefuelDto implements HasVehicleEvent, HasCost {

    public static final RefuelDto ZERO = RefuelDto.builder()//
            .costInCents(0)//
            .volume(0)//
            .station("")//
            .vehicleEvent(VehicleEventDto.ZERO)//
            .build();

    Long id;
    VehicleEventDto vehicleEvent;
    Integer costInCents;
    Integer volume;
    String station;
    Long vehicleId;
}
