package com.kasztelanic.carcare.service;

import com.kasztelanic.carcare.service.dto.AverageConsumptionResult;
import com.kasztelanic.carcare.service.dto.CostRequest;
import com.kasztelanic.carcare.service.dto.CostResult;
import com.kasztelanic.carcare.service.dto.MileageResult;
import com.kasztelanic.carcare.service.dto.PeriodVehicle;

import java.util.List;
import java.util.Optional;

public interface StatisticService {

    AverageConsumptionResult calculateAverageConsumptionPerPeriod(PeriodVehicle periodVehicle);

    List<AverageConsumptionResult> calculateAverageConsumptionPerRefuel(PeriodVehicle periodVehicle);

    Optional<MileageResult> calculateMileageStats(PeriodVehicle periodVehicle);

    List<CostResult> calculate(CostRequest costRequest);
}
