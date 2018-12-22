package com.kasztelanic.carcare.service.dto;

import java.time.LocalDate;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@EqualsAndHashCode(callSuper = true, of = {})
@ToString(includeFieldNames = false, of = {})
public class AverageConsumptionResult extends PeriodVehicleResult {

    @Getter
    private final int volume;
    @Getter
    private final int mileage;

    @Builder
    private AverageConsumptionResult(VehicleDto vehicle, LocalDate dateFrom, LocalDate dateTo, int volume,
            int mileage) {
        super(vehicle, dateFrom, dateTo);
        this.volume = volume;
        this.mileage = mileage;
    }

    public double getAverageConsumption() {
        return volume * 100.0 / mileage;
    }
}
