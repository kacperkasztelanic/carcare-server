package com.kasztelanic.carcare.service.mapper;

import com.kasztelanic.carcare.domain.Vehicle;
import com.kasztelanic.carcare.repository.InspectionRepository;
import com.kasztelanic.carcare.repository.InsuranceRepository;
import com.kasztelanic.carcare.repository.RefuelRepository;
import com.kasztelanic.carcare.repository.RepairRepository;
import com.kasztelanic.carcare.repository.RoutineServiceRepository;
import com.kasztelanic.carcare.service.dto.VehicleRichDto;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class VehicleRichMapper {

    private final FuelTypeMapper fuelTypeMapper;
    private final VehicleDetailsMapper vehicleDetailsMapper;
    private final InsuranceRepository insuranceRepository;
    private final InsuranceMapper insuranceMapper;
    private final InspectionRepository inspectionRepository;
    private final InspectionMapper inspectionMapper;
    private final RepairRepository repairRepository;
    private final RepairMapper repairMapper;
    private final RoutineServiceRepository routineServiceRepository;
    private final RoutineServiceMapper routineServiceMapper;
    private final RefuelRepository refuelRepository;
    private final RefuelMapper refuelMapper;

    @Autowired
    @SuppressWarnings("all")
    public VehicleRichMapper(FuelTypeMapper fuelTypeMapper, VehicleDetailsMapper vehicleDetailsMapper,
            InsuranceRepository insuranceRepository, InsuranceMapper insuranceMapper,
            InspectionRepository inspectionRepository, InspectionMapper inspectionMapper, RefuelMapper refuelMapper,
            RepairRepository repairRepository, RepairMapper repairMapper,
            RoutineServiceRepository routineServiceRepository, RoutineServiceMapper routineServiceMapper,
            RefuelRepository refuelRepository) {
        this.fuelTypeMapper = fuelTypeMapper;
        this.vehicleDetailsMapper = vehicleDetailsMapper;
        this.insuranceRepository = insuranceRepository;
        this.insuranceMapper = insuranceMapper;
        this.inspectionRepository = inspectionRepository;
        this.inspectionMapper = inspectionMapper;
        this.refuelMapper = refuelMapper;
        this.repairRepository = repairRepository;
        this.repairMapper = repairMapper;
        this.routineServiceRepository = routineServiceRepository;
        this.routineServiceMapper = routineServiceMapper;
        this.refuelRepository = refuelRepository;
    }

    public VehicleRichDto vehicleToVehicleDto(Vehicle vehicle) {
        return VehicleRichDto.richBuilder()//
                .id(vehicle.getId())//
                .make(vehicle.getMake())//
                .model(vehicle.getModel())//
                .licensePlate(vehicle.getLicensePlate())//
                .fuelType(fuelTypeMapper.fuelTypeToFuelTypeDto(vehicle.getFuelType(),
                        Locale.forLanguageTag(vehicle.getOwner().getLangKey())))//
                .vehicleDetails(vehicleDetailsMapper.vehicleDetailsToVehicleDetailsDto(vehicle))//
                .insurance(insuranceRepository.findByVehicleId(vehicle.getId()).stream()//
                        .map(insuranceMapper::insuranceToInsuranceDto)//
                        .collect(Collectors.toSet()))//
                .inspection(inspectionRepository.findByVehicleId(vehicle.getId()).stream()//
                        .map(inspectionMapper::inspectionToInspectionDto)//
                        .collect(Collectors.toSet()))//
                .routineService(routineServiceRepository.findByVehicleId(vehicle.getId()).stream()//
                        .map(routineServiceMapper::routineServiceToRoutineServiceDto)//
                        .collect(Collectors.toSet()))//
                .repair(repairRepository.findByVehicleId(vehicle.getId()).stream()//
                        .map(repairMapper::repairToRepairDto)//
                        .collect(Collectors.toSet()))//
                .refuel(refuelRepository.findByVehicleId(vehicle.getId()).stream()//
                        .map(refuelMapper::refuelToRefuelDto)//
                        .collect(Collectors.toSet()))//
                .build();
    }
}
