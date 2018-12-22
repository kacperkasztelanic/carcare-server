package com.kasztelanic.carcare.service.dto;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Builder
@EqualsAndHashCode(of = { "periodVehicle" })
@ToString(of = { "periodVehicle" }, includeFieldNames = false)
public class AverageConsumptionResult {

    @Getter
    private final PeriodVehicle periodVehicle;
    @Getter
    private final double volume;
    @Getter
    private final int mileage;

    public double getAverageConsumption() {
        return volume * 100.0 / mileage;
    }
}
