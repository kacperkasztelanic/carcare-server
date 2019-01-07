package com.kasztelanic.carcare.service.dto;

import java.util.Set;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@EqualsAndHashCode(callSuper = true, of = {})
@ToString(callSuper = true, of = {}, includeFieldNames = false)
public class VehicleRichDto extends VehicleDto {

    @Getter
    private final Set<InsuranceDto> insurance;
    @Getter
    private final Set<InspectionDto> inspection;
    @Getter
    private final Set<RoutineServiceDto> routineService;
    @Getter
    private final Set<RepairDto> repair;
    @Getter
    private final Set<RefuelDto> refuel;

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
