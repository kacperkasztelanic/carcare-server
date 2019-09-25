package com.kasztelanic.carcare.service;

import com.kasztelanic.carcare.domain.User;
import com.kasztelanic.carcare.service.dto.VehicleDto;

import java.util.List;
import java.util.Optional;

public interface VehicleService {

    Optional<VehicleDto> getVehicle(Long id);

    List<VehicleDto> getAllVehicles();

    VehicleDto addVehicle(VehicleDto vehicleDto, User user);

    Optional<VehicleDto> editVehicle(Long id, VehicleDto vehicleDto);

    Optional<VehicleDto> deleteVehicle(Long id);
}
