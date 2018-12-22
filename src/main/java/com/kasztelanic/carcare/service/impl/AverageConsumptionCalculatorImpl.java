package com.kasztelanic.carcare.service.impl;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import com.kasztelanic.carcare.service.AverageConsumptionCalculator;
import com.kasztelanic.carcare.service.dto.AverageConsumptionResult;
import com.kasztelanic.carcare.service.dto.RefuelDto;
import com.kasztelanic.carcare.service.dto.VehicleDto;

public class AverageConsumptionCalculatorImpl implements AverageConsumptionCalculator {

    @Override
    public AverageConsumptionResult calculate(VehicleDto vehicle, LocalDate dateFrom, LocalDate dateTo) {
        List<RefuelDto> refuels = vehicle.getRefuel().stream()
                .filter(i -> !i.getVehicleEvent().getDate().isBefore(dateFrom)
                        && !i.getVehicleEvent().getDate().isAfter(dateTo))
                .collect(Collectors.toList());
        int volume = 0;
        int mileage = 0;
        for (RefuelDto refuel : refuels) {
            volume += refuel.getVolume();
            volume += refuel.getVehicleEvent().getMileage();
        }
        return AverageConsumptionResult.builder().vehicle(vehicle).dateFrom(dateFrom).dateTo(dateTo).mileage(mileage)
                .volume(volume).build();
    }
}
