package com.kasztelanic.carcare.service;

import java.util.Collection;
import java.util.List;

import com.kasztelanic.carcare.service.dto.AverageConsumptionResult;
import com.kasztelanic.carcare.service.dto.PeriodVehicle;
import com.kasztelanic.carcare.service.dto.RefuelDto;

public interface AverageConsumptionCalculator {

    AverageConsumptionResult calculate(PeriodVehicle periodVehicle, Collection<RefuelDto> refuels);

    List<AverageConsumptionResult> calculatePerRefuel(PeriodVehicle periodVehicle, Collection<RefuelDto> refuels);
}
