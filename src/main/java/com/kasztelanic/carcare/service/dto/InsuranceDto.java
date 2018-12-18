package com.kasztelanic.carcare.service.dto;

import java.time.LocalDate;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Builder
@EqualsAndHashCode(of = { "uuid" })
@ToString(of = { "validThru", "vehicleEvent" })
public class InsuranceDto {

    @Getter
    private final Long id;
    @Getter
    private final String uuid;
    @Getter
    private final VehicleEventDto vehicleEvent;
    @Getter
    private final LocalDate validFrom;
    @Getter
    private final LocalDate validThru;
    @Getter
    private final Integer costInCents;
    @Getter
    private final String number;
    @Getter
    private final String insurer;
    @Getter
    private final String details;
    @Getter
    private final String insuranceType;
}
