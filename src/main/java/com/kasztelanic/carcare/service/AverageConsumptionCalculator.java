package com.kasztelanic.carcare.service;

import java.time.LocalDate;

import com.kasztelanic.carcare.service.dto.AverageConsumptionResult;
import com.kasztelanic.carcare.service.dto.VehicleDto;

public interface AverageConsumptionCalculator {

    AverageConsumptionResult calculate(VehicleDto vehicle, LocalDate dateFrom, LocalDate dateTo);
}
