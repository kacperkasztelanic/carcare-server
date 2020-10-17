package com.kasztelanic.carcare.service.impl;

import com.kasztelanic.carcare.service.CostCalculator;
import com.kasztelanic.carcare.service.dto.CostResult;
import com.kasztelanic.carcare.service.dto.HasCost;
import com.kasztelanic.carcare.service.dto.HasVehicleEvent;
import com.kasztelanic.carcare.service.dto.PeriodVehicle;
import com.kasztelanic.carcare.service.dto.VehicleRichDto;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collection;

@Service
public class CostCalculatorImpl implements CostCalculator {

    @Override
    public CostResult calculate(PeriodVehicle periodVehicle, VehicleRichDto vehicle) {
        LocalDate dateFrom = periodVehicle.getDateFrom();
        LocalDate dateTo = periodVehicle.getDateTo();
        return CostResult.builder()//
                .periodVehicle(periodVehicle)//
                .insuranceCosts(sumCostsBetweenDates(vehicle.getInsurance(), dateFrom, dateTo))//
                .inspectionCosts(sumCostsBetweenDates(vehicle.getInspection(), dateFrom, dateTo))//
                .routineServiceCosts(sumCostsBetweenDates(vehicle.getRoutineService(), dateFrom, dateTo))//
                .repairCosts(sumCostsBetweenDates(vehicle.getRepair(), dateFrom, dateTo))//
                .refuelCosts(sumCostsBetweenDates(vehicle.getRefuel(), dateFrom, dateTo))//
                .build();
    }

    private static <T extends HasCost & HasVehicleEvent> double sumCostsBetweenDates(Collection<T> collection,
            LocalDate dateFrom, LocalDate dateTo) {
        return collection.stream()//
                .filter(i -> !i.getVehicleEvent().getDate().isBefore(dateFrom)//
                        && !i.getVehicleEvent().getDate().isAfter(dateTo)//
                )//
                .mapToInt(HasCost::getCostInCents).sum() / 100.0;
    }
}
