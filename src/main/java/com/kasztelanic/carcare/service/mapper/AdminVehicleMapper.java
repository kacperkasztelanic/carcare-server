package com.kasztelanic.carcare.service.mapper;

import com.kasztelanic.carcare.domain.Vehicle;
import com.kasztelanic.carcare.service.dto.AdminVehicleDto;
import org.springframework.stereotype.Service;

@Service
public class AdminVehicleMapper {

    public AdminVehicleDto vehicleToAdminVehicleDto(Vehicle vehicle) {
        return AdminVehicleDto.builder()
            .id(vehicle.getId())
            .ownerLogin(vehicle.getOwner().getLogin())
            .make(vehicle.getMake())
            .model(vehicle.getModel())
            .licensePlate(vehicle.getLicensePlate())
            .archivedAt(vehicle.getArchivedAt())
            .build();
    }
}
