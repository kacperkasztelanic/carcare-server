package com.kasztelanic.carcare.service.impl;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.kasztelanic.carcare.service.AverageConsumptionCalculator;
import com.kasztelanic.carcare.service.dto.AverageConsumptionResult;
import com.kasztelanic.carcare.service.dto.PeriodVehicle;
import com.kasztelanic.carcare.service.dto.RefuelDto;

@Service
public class AverageConsumptionCalculatorImpl implements AverageConsumptionCalculator {

    @Override
    public AverageConsumptionResult calculate(PeriodVehicle periodVehicle, Collection<RefuelDto> refuels) {
        List<RefuelDto> filteredRefuels = refuels.stream()
                .filter(i -> !i.getVehicleEvent().getDate().isBefore(periodVehicle.getDateFrom())
                        && !i.getVehicleEvent().getDate().isAfter(periodVehicle.getDateTo()))
                .collect(Collectors.toList());
        int volume = 0;
        int mileage = 0;
        for (RefuelDto refuel : filteredRefuels) {
            volume += refuel.getVolume();
            volume += refuel.getVehicleEvent().getMileage();
        }
        return AverageConsumptionResult.builder().periodVehicle(periodVehicle).mileage(mileage).volume(volume).build();
    }
}
