package com.kasztelanic.carcare.service.dto;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Builder
@EqualsAndHashCode(of = { "periodVehicle" })
@ToString(of = { "periodVehicle" }, includeFieldNames = false)
public class CostResult {

    @Getter
    private final PeriodVehicle periodVehicle;
    @Getter
    private final double insuranceCosts;
    @Getter
    private final double inspectionCosts;
    @Getter
    private final double repairCosts;
    @Getter
    private final double routineServiceCosts;
    @Getter
    private final double refuelCosts;

    public double getSum() {
        return insuranceCosts + inspectionCosts + routineServiceCosts + repairCosts + refuelCosts;
    }
}
