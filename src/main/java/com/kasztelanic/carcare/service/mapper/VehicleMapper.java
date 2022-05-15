package com.kasztelanic.carcare.service.mapper;

import com.kasztelanic.carcare.domain.Vehicle;
import com.kasztelanic.carcare.service.dto.VehicleDetailsDto;
import com.kasztelanic.carcare.service.dto.VehicleDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class VehicleMapper {

    private final FuelTypeMapper fuelTypeMapper;
    private final VehicleDetailsMapper vehicleDetailsMapper;

    @Autowired
    public VehicleMapper(FuelTypeMapper fuelTypeMapper, VehicleDetailsMapper vehicleDetailsMapper) {
        this.fuelTypeMapper = fuelTypeMapper;
        this.vehicleDetailsMapper = vehicleDetailsMapper;
    }

    public VehicleDto vehicleToVehicleDto(Vehicle vehicle) {
        return VehicleDto.builder()//
            .id(vehicle.getId())//
            .make(vehicle.getMake())//
            .model(vehicle.getModel())//
            .licensePlate(vehicle.getLicensePlate())//
            .fuelType(fuelTypeMapper.fuelTypeToFuelTypeDto(vehicle.getFuelType(),
                Locale.forLanguageTag(vehicle.getOwner().getLangKey())))//
            .vehicleDetails(vehicleDetailsMapper.vehicleDetailsToVehicleDetailsDto(vehicle))//
            .build();
    }

    public Vehicle vehicleDtoToVehicle(VehicleDto vehicleDto) {
        return Vehicle.builder()//
            .make(vehicleDto.getMake().trim())//
            .model(vehicleDto.getModel().trim())//
            .licensePlate(vehicleDto.getLicensePlate().trim())//
            .fuelType(fuelTypeMapper.fuelTypeDtoToFuelType(vehicleDto.getFuelType()))//
            .vehicleDetails(vehicleDetailsMapper.vehicleDetailsDtoToVehicleDetails(
                vehicleDto.getVehicleDetails() != null ? vehicleDto.getVehicleDetails() : VehicleDetailsDto
                    .defaultBuilder()//
                    .vehicleId(vehicleDto.getId())//
                    .build())//
            )//
            .build();
    }
}
