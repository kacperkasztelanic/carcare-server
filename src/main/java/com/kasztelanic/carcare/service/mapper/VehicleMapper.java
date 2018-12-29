package com.kasztelanic.carcare.service.mapper;

import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.kasztelanic.carcare.domain.Vehicle;
import com.kasztelanic.carcare.domain.Vehicle.VehicleBuilder;
import com.kasztelanic.carcare.repository.FuelTypeRepository;
import com.kasztelanic.carcare.service.dto.VehicleDto;
import com.kasztelanic.carcare.service.dto.VehicleDto.VehicleDtoBuilder;

@Service
public class VehicleMapper {

    @Autowired
    private FuelTypeRepository fuelTypeRepository;

    @Autowired
    private VehicleDetailsMapper vehicleDetailsMapper;

    @Autowired
    private InsuranceMapper insuranceMapper;

    @Autowired
    private InspectionMapper inspectionMapper;

    @Autowired
    private RepairMapper repairMapper;

    @Autowired
    private RoutineServiceMapper routineServiceMapper;

    @Autowired
    private RefuelMapper refuelMapper;

    public VehicleDto vehicleToVehicleDto(Vehicle vehicle) {
        VehicleDtoBuilder builder = VehicleDto.builder();
        builder.id(vehicle.getId());
        builder.make(vehicle.getMake());
        builder.model(vehicle.getModel());
        builder.licensePlate(vehicle.getLicensePlate());
        builder.fuelType(vehicle.getFuelType().getType());
        builder.vehicleDetails(vehicleDetailsMapper.vehicleDetailsToVehicleDetailsDto(vehicle));
        builder.insurance(vehicle.getInsurance().stream().map(insuranceMapper::insuranceToInsuranceDto)
                .collect(Collectors.toSet()));
        builder.inspection(vehicle.getInspection().stream().map(inspectionMapper::inspectionToInspectionDto)
                .collect(Collectors.toSet()));
        builder.routineService(vehicle.getRoutineService().stream()
                .map(routineServiceMapper::routineServiceToRoutineServiceDto).collect(Collectors.toSet()));
        builder.repair(vehicle.getRepair().stream().map(repairMapper::repairToRepairDto).collect(Collectors.toSet()));
        builder.refuel(vehicle.getRefuel().stream().map(refuelMapper::refuelToRefuelDto).collect(Collectors.toSet()));
        return builder.build();
    }

    public Vehicle vehicleDtoToVehicle(VehicleDto vehicleDto) {
        VehicleBuilder builder = Vehicle.builder();
        builder.make(vehicleDto.getMake());
        builder.model(vehicleDto.getModel());
        builder.licensePlate(vehicleDto.getLicensePlate());
        builder.fuelType(
                fuelTypeRepository.findByType(vehicleDto.getFuelType()).orElseThrow(IllegalStateException::new));
        builder.vehicleDetails(vehicleDetailsMapper.vehicleDetailsDtoToVehicleDetails(vehicleDto.getVehicleDetails()));
        builder.insurance(vehicleDto.getInsurance().stream().map(insuranceMapper::insuranceDtoToInsurance)
                .collect(Collectors.toSet()));
        builder.inspection(vehicleDto.getInspection().stream().map(inspectionMapper::inspectionDtoToInspection)
                .collect(Collectors.toSet()));
        builder.routineService(vehicleDto.getRoutineService().stream()
                .map(routineServiceMapper::routineServiceDtoToRoutineService).collect(Collectors.toSet()));
        builder.repair(
                vehicleDto.getRepair().stream().map(repairMapper::repairDtoToRepair).collect(Collectors.toSet()));
        builder.refuel(
                vehicleDto.getRefuel().stream().map(refuelMapper::refuelDtoToRefuel).collect(Collectors.toSet()));
        return builder.build();
    }
}
