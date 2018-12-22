package com.kasztelanic.carcare.service;

import java.time.LocalDate;

import com.kasztelanic.carcare.service.dto.CostResult;
import com.kasztelanic.carcare.service.dto.VehicleDto;

public interface CostCalculator {

    CostResult calculate(VehicleDto vehicle, LocalDate dateFrom, LocalDate dateTo);
}
