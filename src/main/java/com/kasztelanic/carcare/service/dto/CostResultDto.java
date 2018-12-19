package com.kasztelanic.carcare.service.dto;

import java.time.LocalDate;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Builder
@EqualsAndHashCode(of = { "vehicle", "dateFrom", "dateTo" })
@ToString(of = { "vehicle", "dateFrom", "dateTo" }, includeFieldNames = false)
public class CostResultDto {

    @Getter
    private final VehicleDto vehicle;
    @Getter
    private final LocalDate dateFrom;
    @Getter
    private final LocalDate dateTo;
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
