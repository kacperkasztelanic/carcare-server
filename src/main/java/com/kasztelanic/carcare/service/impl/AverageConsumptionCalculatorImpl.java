package com.kasztelanic.carcare.service.impl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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
                .sorted(Comparator.comparing((RefuelDto r) -> r.getVehicleEvent().getDate()).reversed())
                .collect(Collectors.toList());
        int maxMileage = filteredRefuels.stream().mapToInt(r -> r.getVehicleEvent().getMileage()).max().orElse(0);
        int minMileage = filteredRefuels.stream().mapToInt(r -> r.getVehicleEvent().getMileage()).min().orElse(0);
        int volume = filteredRefuels.stream().skip(1).mapToInt(RefuelDto::getVolume).sum();
        return AverageConsumptionResult.builder().periodVehicle(periodVehicle).mileage(maxMileage - minMileage)
                .volume(volume / 1000.0).build();
    }

    @Override
    public List<AverageConsumptionResult> calculatePerRefuel(PeriodVehicle periodVehicle,
            Collection<RefuelDto> refuels) {
        List<RefuelDto> filteredRefuels = refuels.stream()
                .filter(i -> !i.getVehicleEvent().getDate().isBefore(periodVehicle.getDateFrom())
                        && !i.getVehicleEvent().getDate().isAfter(periodVehicle.getDateTo()))
                .sorted(Comparator.comparing((RefuelDto r) -> r.getVehicleEvent().getDate()))
                .collect(Collectors.toList());
        List<AverageConsumptionResult> result = new ArrayList<>();
        for (int i = 0; i < filteredRefuels.size() - 1; i++) {
            int volume = filteredRefuels.get(i).getVolume();
            int mileage = filteredRefuels.get(i + 1).getVehicleEvent().getMileage()
                    - filteredRefuels.get(i).getVehicleEvent().getMileage();
            LocalDate dateFrom = filteredRefuels.get(i).getVehicleEvent().getDate();
            LocalDate dateTo = filteredRefuels.get(i + 1).getVehicleEvent().getDate();
            result.add(AverageConsumptionResult.builder().mileage(mileage).volume(volume / 1000.0)
                    .periodVehicle(PeriodVehicle.of(periodVehicle.getVehicleId(), dateFrom, dateTo)).build());
        }
        return result;
    }
}
