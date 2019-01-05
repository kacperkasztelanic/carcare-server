package com.kasztelanic.carcare.service.mapper;

import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.kasztelanic.carcare.domain.Vehicle;
import com.kasztelanic.carcare.repository.InspectionRepository;
import com.kasztelanic.carcare.repository.InsuranceRepository;
import com.kasztelanic.carcare.repository.RefuelRepository;
import com.kasztelanic.carcare.repository.RepairRepository;
import com.kasztelanic.carcare.repository.RoutineServiceRepository;
import com.kasztelanic.carcare.service.dto.VehicleRichDto;
import com.kasztelanic.carcare.service.dto.VehicleRichDto.VehicleRichDtoBuilder;

@Service
public class VehicleRichMapper {

    @Autowired
    private VehicleDetailsMapper vehicleDetailsMapper;
    @Autowired
    private InsuranceRepository insuranceRepository;
    @Autowired
    private InsuranceMapper insuranceMapper;
    @Autowired
    private InspectionRepository inspectionRepository;
    @Autowired
    private InspectionMapper inspectionMapper;
    @Autowired
    private RepairRepository repairRepository;
    @Autowired
    private RepairMapper repairMapper;
    @Autowired
    private RoutineServiceRepository routineServiceRepository;
    @Autowired
    private RoutineServiceMapper routineServiceMapper;
    @Autowired
    private RefuelRepository refuelRepository;
    @Autowired
    private RefuelMapper refuelMapper;

    public VehicleRichDto vehicleToVehicleDto(Vehicle vehicle) {
        VehicleRichDtoBuilder builder = VehicleRichDto.richBuilder();
        builder.id(vehicle.getId());
        builder.make(vehicle.getMake());
        builder.model(vehicle.getModel());
        builder.licensePlate(vehicle.getLicensePlate());
        builder.fuelType(vehicle.getFuelType().getType());
        builder.vehicleDetails(vehicleDetailsMapper.vehicleDetailsToVehicleDetailsDto(vehicle));
        builder.insurance(insuranceRepository.findByVehicleId(vehicle.getId()).stream()
                .map(insuranceMapper::insuranceToInsuranceDto).collect(Collectors.toSet()));
        builder.inspection(inspectionRepository.findByVehicleId(vehicle.getId()).stream()
                .map(inspectionMapper::inspectionToInspectionDto).collect(Collectors.toSet()));
        builder.routineService(routineServiceRepository.findByVehicleId(vehicle.getId()).stream()
                .map(routineServiceMapper::routineServiceToRoutineServiceDto).collect(Collectors.toSet()));
        builder.repair(repairRepository.findByVehicleId(vehicle.getId()).stream().map(repairMapper::repairToRepairDto)
                .collect(Collectors.toSet()));
        builder.refuel(refuelRepository.findByVehicleId(vehicle.getId()).stream().map(refuelMapper::refuelToRefuelDto)
                .collect(Collectors.toSet()));
        return builder.build();
    }
}
