package com.kasztelanic.carcare.service;

import com.kasztelanic.carcare.service.dto.AverageConsumptionResult;
import com.kasztelanic.carcare.service.dto.PeriodVehicle;
import com.kasztelanic.carcare.service.dto.RefuelDto;

import java.util.Collection;
import java.util.List;

public interface AverageConsumptionCalculator {

    AverageConsumptionResult calculate(PeriodVehicle periodVehicle, Collection<RefuelDto> refuels);

    List<AverageConsumptionResult> calculatePerRefuel(PeriodVehicle periodVehicle, Collection<RefuelDto> refuels);
}
