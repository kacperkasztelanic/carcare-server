package com.kasztelanic.carcare.service.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

@Value(staticConstructor = "of")
@EqualsAndHashCode(of = { "periodVehicle" })
@ToString(of = { "periodVehicle" }, includeFieldNames = false)
public class AverageConsumptionResult {

    PeriodVehicle periodVehicle;
    double volume;
    int mileage;

    public double getAverageConsumption() {
        return BigDecimal.valueOf(volume * 100.0 / mileage)
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
