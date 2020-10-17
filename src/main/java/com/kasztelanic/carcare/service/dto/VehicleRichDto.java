package com.kasztelanic.carcare.service.dto;

import java.util.Set;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true, of = {})
@ToString(callSuper = true, of = {}, includeFieldNames = false)
public class VehicleRichDto extends VehicleDto {

     Set<InsuranceDto> insurance;
     Set<InspectionDto> inspection;
     Set<RoutineServiceDto> routineService;
     Set<RepairDto> repair;
     Set<RefuelDto> refuel;

    @Builder(builderMethodName = "richBuilder")
    @SuppressWarnings("all")
    private VehicleRichDto(Long id, String make, String model, String licensePlate, FuelTypeDto fuelType,
            VehicleDetailsDto vehicleDetails, Set<InsuranceDto> insurance, Set<InspectionDto> inspection,
            Set<RoutineServiceDto> routineService, Set<RepairDto> repair, Set<RefuelDto> refuel) {
        super(id, make, model, licensePlate, fuelType, vehicleDetails);
        this.insurance = insurance;
        this.inspection = inspection;
        this.routineService = routineService;
        this.repair = repair;
        this.refuel = refuel;
    }
}
