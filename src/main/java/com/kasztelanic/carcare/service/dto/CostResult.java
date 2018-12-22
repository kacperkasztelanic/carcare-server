package com.kasztelanic.carcare.service.dto;

import java.time.LocalDate;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@EqualsAndHashCode(callSuper = true, of = {})
@ToString(callSuper = true, of = {}, includeFieldNames = false)
public class CostResult extends PeriodVehicleResult {

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

    @Builder
    @SuppressWarnings("all")
    private CostResult(VehicleDto vehicle, LocalDate dateFrom, LocalDate dateTo, double insuranceCosts,
            double inspectionCosts, double repairCosts, double routineServiceCosts, double refuelCosts) {
        super(vehicle, dateFrom, dateTo);
        this.insuranceCosts = insuranceCosts;
        this.inspectionCosts = inspectionCosts;
        this.repairCosts = repairCosts;
        this.routineServiceCosts = routineServiceCosts;
        this.refuelCosts = refuelCosts;
    }

    public double getSum() {
        return insuranceCosts + inspectionCosts + routineServiceCosts + repairCosts + refuelCosts;
    }
}
