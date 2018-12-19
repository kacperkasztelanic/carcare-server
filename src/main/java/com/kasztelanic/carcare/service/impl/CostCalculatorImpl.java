package com.kasztelanic.carcare.service.impl;

import java.time.LocalDate;
import java.util.Collection;

import org.springframework.stereotype.Service;

import com.kasztelanic.carcare.service.CostCalculator;
import com.kasztelanic.carcare.service.dto.CostResultDto;
import com.kasztelanic.carcare.service.dto.CostResultDto.CostResultDtoBuilder;
import com.kasztelanic.carcare.service.dto.HasCost;
import com.kasztelanic.carcare.service.dto.HasVehicleEvent;
import com.kasztelanic.carcare.service.dto.VehicleDto;

@Service
public class CostCalculatorImpl implements CostCalculator {

    @Override
    public CostResultDto calculate(VehicleDto vehicle, LocalDate dateFrom, LocalDate dateTo) {
        CostResultDtoBuilder builder = CostResultDto.builder();
        builder.vehicle(vehicle);
        builder.dateFrom(dateFrom);
        builder.dateTo(dateTo);
        builder.insuranceCosts(sumCostsBetweenDates(vehicle.getInsurance(), dateFrom, dateTo));
        builder.inspectionCosts(sumCostsBetweenDates(vehicle.getInspection(), dateFrom, dateTo));
        builder.routineServiceCosts(sumCostsBetweenDates(vehicle.getRoutineService(), dateFrom, dateTo));
        builder.repairCosts(sumCostsBetweenDates(vehicle.getRepair(), dateFrom, dateTo));
        builder.refuelCosts(sumCostsBetweenDates(vehicle.getRefuel(), dateFrom, dateTo));
        return builder.build();
    }

    private static <T extends HasCost & HasVehicleEvent> double sumCostsBetweenDates(Collection<T> collection,
            LocalDate dateFrom, LocalDate dateTo) {
        return collection.stream().filter(i -> !i.getVehicleEvent().getDate().isBefore(dateFrom)
                && !i.getVehicleEvent().getDate().isAfter(dateTo)).mapToInt(HasCost::getCostInCents).sum() / 100.0;
    }
}
