package com.kasztelanic.carcare.service.mapper;

import org.springframework.stereotype.Service;

import com.kasztelanic.carcare.domain.Vehicle;
import com.kasztelanic.carcare.service.dto.VehicleDto;

@Service
public class VehicleMapper {

    public VehicleDto vehicleToVehicleDto(Vehicle vehicle) {
        VehicleDto.VehicleDtoBuilder builder = VehicleDto.builder();
        builder.id(vehicle.getId());
        builder.uuid(vehicle.getUuid());
        builder.make(vehicle.getMake());
        builder.model(vehicle.getModel());
        builder.licensePlate(vehicle.getLicensePlate());
        return builder.build();
    }
}
