package com.kasztelanic.carcare.service;

import java.time.LocalDate;

import com.kasztelanic.carcare.service.dto.CostResultDto;
import com.kasztelanic.carcare.service.dto.VehicleDto;

public interface CostCalculator {

    CostResultDto calculate(VehicleDto vehicle, LocalDate dateFrom, LocalDate dateTo);
}
