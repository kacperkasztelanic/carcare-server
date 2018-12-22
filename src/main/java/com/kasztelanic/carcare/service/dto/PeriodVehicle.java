package com.kasztelanic.carcare.service.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@AllArgsConstructor(staticName = "of")
@EqualsAndHashCode
@ToString(includeFieldNames = false)
public class PeriodVehicle {

    @Getter
    protected final Long vehicleId;
    @Getter
    protected final LocalDate dateFrom;
    @Getter
    protected final LocalDate dateTo;
}
