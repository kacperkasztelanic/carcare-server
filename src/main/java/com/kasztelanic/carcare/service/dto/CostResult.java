package com.kasztelanic.carcare.service.dto;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

@Value
@Builder
@EqualsAndHashCode(of = { "periodVehicle" })
@ToString(of = { "periodVehicle" }, includeFieldNames = false)
public class CostResult {

    PeriodVehicle periodVehicle;
    double insuranceCosts;
    double inspectionCosts;
    double repairCosts;
    double routineServiceCosts;
    double refuelCosts;

    public double getSum() {
        return insuranceCosts + inspectionCosts + routineServiceCosts + repairCosts + refuelCosts;
    }
}
