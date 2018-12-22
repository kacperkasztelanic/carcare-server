package com.kasztelanic.carcare.service.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@AllArgsConstructor
@EqualsAndHashCode
@ToString(includeFieldNames = false)
public class PeriodVehicleRequest {

    @Getter
    protected final Long vehicleId;
    @Getter
    protected final LocalDate dateFrom;
    @Getter
    protected final LocalDate dateTo;
}
