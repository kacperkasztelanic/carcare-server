package com.kasztelanic.carcare.service;

import com.kasztelanic.carcare.service.dto.MileageResult;
import com.kasztelanic.carcare.service.dto.PeriodVehicle;
import com.kasztelanic.carcare.service.dto.VehicleRichDto;

public interface MileageService {

    MileageResult calculate(PeriodVehicle periodVehicle, VehicleRichDto vehicleRichDto);
}
