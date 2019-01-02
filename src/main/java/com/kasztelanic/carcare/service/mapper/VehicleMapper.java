package com.kasztelanic.carcare.service.mapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.kasztelanic.carcare.domain.Vehicle;
import com.kasztelanic.carcare.domain.Vehicle.VehicleBuilder;
import com.kasztelanic.carcare.repository.FuelTypeRepository;
import com.kasztelanic.carcare.service.dto.VehicleDetailsDto;
import com.kasztelanic.carcare.service.dto.VehicleDto;
import com.kasztelanic.carcare.service.dto.VehicleDto.VehicleDtoBuilder;

@Service
public class VehicleMapper {

    @Autowired
    private FuelTypeRepository fuelTypeRepository;

    @Autowired
    private VehicleDetailsMapper vehicleDetailsMapper;

    public VehicleDto vehicleToVehicleDto(Vehicle vehicle) {
        VehicleDtoBuilder builder = VehicleDto.builder();
        builder.id(vehicle.getId());
        builder.make(vehicle.getMake());
        builder.model(vehicle.getModel());
        builder.licensePlate(vehicle.getLicensePlate());
        builder.fuelType(vehicle.getFuelType().getType());
        builder.vehicleDetails(vehicleDetailsMapper.vehicleDetailsToVehicleDetailsDto(vehicle));
        return builder.build();
    }

    public Vehicle vehicleDtoToVehicle(VehicleDto vehicleDto) {
        VehicleBuilder builder = Vehicle.builder();
        builder.make(vehicleDto.getMake());
        builder.model(vehicleDto.getModel());
        builder.licensePlate(vehicleDto.getLicensePlate());
        builder.fuelType(
                fuelTypeRepository.findByType(vehicleDto.getFuelType()).orElseThrow(IllegalStateException::new));
        builder.vehicleDetails(vehicleDetailsMapper.vehicleDetailsDtoToVehicleDetails(
                vehicleDto.getVehicleDetails() != null ? vehicleDto.getVehicleDetails()
                        : VehicleDetailsDto.defaultBuilder().vehicleId(vehicleDto.getId()).build()));
        return builder.build();
    }
}
