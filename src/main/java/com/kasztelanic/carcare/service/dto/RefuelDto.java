package com.kasztelanic.carcare.service.dto;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Builder
@EqualsAndHashCode(of = { "id" })
// @ToString(of = { "costInCents", "volume" }, includeFieldNames = false)
@ToString
public class RefuelDto implements HasVehicleEvent, HasCost {

    @Getter
    private final Long id;
    @Getter
    private final VehicleEventDto vehicleEvent;
    @Getter
    private final Integer costInCents;
    @Getter
    private final Integer volume;
    @Getter
    private final String station;
    @Getter
    private final Long vehicleId;

    public static final RefuelDto ZERO = RefuelDto.builder().costInCents(0).volume(0).station("")
            .vehicleEvent(VehicleEventDto.ZERO).build();
}
