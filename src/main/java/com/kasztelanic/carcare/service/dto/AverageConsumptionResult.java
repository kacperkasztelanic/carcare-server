package com.kasztelanic.carcare.service.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

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
        return BigDecimal.valueOf(volume * 100.0 / mileage)
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
