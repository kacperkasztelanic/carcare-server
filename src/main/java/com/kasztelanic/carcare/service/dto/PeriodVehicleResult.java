package com.kasztelanic.carcare.service.dto;

import java.time.LocalDate;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@AllArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
@ToString(includeFieldNames = false)
public abstract class PeriodVehicleResult {

    @Getter
    protected final VehicleDto vehicle;
    @Getter
    protected final LocalDate dateFrom;
    @Getter
    protected final LocalDate dateTo;
}
