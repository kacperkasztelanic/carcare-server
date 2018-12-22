package com.kasztelanic.carcare.service;

import com.kasztelanic.carcare.service.dto.CostResult;
import com.kasztelanic.carcare.service.dto.PeriodVehicle;
import com.kasztelanic.carcare.service.dto.VehicleDto;

public interface CostCalculator {

    CostResult calculate(PeriodVehicle periodVehicle, VehicleDto vehicle);
}
