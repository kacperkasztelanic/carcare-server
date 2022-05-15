package com.kasztelanic.carcare.service.impl;

import com.kasztelanic.carcare.domain.User;
import com.kasztelanic.carcare.domain.Vehicle;
import com.kasztelanic.carcare.repository.VehicleRepository;
import com.kasztelanic.carcare.service.ImageStorageService;
import com.kasztelanic.carcare.service.VehicleService;
import com.kasztelanic.carcare.service.dto.VehicleDto;
import com.kasztelanic.carcare.service.mapper.VehicleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VehicleServiceImpl implements VehicleService {

    private final VehicleRepository vehicleRepository;
    private final VehicleMapper vehicleMapper;
    private final ImageStorageService imageStorageService;

    @Override
    @Transactional(readOnly = true)
    public Optional<VehicleDto> getVehicle(Long id) {
        return vehicleRepository.findByIdAndOwnerIsCurrentUser(id)//
            .map(vehicleMapper::vehicleToVehicleDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VehicleDto> getAllVehicles() {
        return vehicleRepository.findByOwnerIsCurrentUser().stream()//
            .map(vehicleMapper::vehicleToVehicleDto)//
            .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public VehicleDto addVehicle(VehicleDto vehicleDto, User user) {
        Vehicle vehicle = vehicleMapper.vehicleDtoToVehicle(vehicleDto);
        vehicle.setOwner(user);
        return vehicleMapper.vehicleToVehicleDto(vehicleRepository.save(vehicle));
    }

    @Override
    @Transactional
    public Optional<VehicleDto> editVehicle(Long id, VehicleDto vehicleDto) {
        return vehicleRepository.findByIdAndOwnerIsCurrentUser(id)//
            .map(i -> updateVehicle(i, vehicleMapper.vehicleDtoToVehicle(vehicleDto)))//
            .map(vehicleRepository::save)//
            .map(vehicleMapper::vehicleToVehicleDto);
    }

    @Override
    @Transactional
    public Optional<VehicleDto> deleteVehicle(Long id) {
        Optional<VehicleDto> vehicle = vehicleRepository.findByIdAndOwnerIsCurrentUser(id)//
            .map(vehicleMapper::vehicleToVehicleDto);
        vehicle.ifPresent(v -> vehicleRepository.deleteById(id));
        return vehicle;
    }

    private Vehicle updateVehicle(Vehicle vehicle, Vehicle updatedVehicle) {
        imageStorageService.delete(vehicle.getVehicleDetails().getImage());
        vehicle.setFuelType(updatedVehicle.getFuelType());
        vehicle.setLicensePlate(updatedVehicle.getLicensePlate());
        vehicle.setMake(updatedVehicle.getMake());
        vehicle.setModel(updatedVehicle.getModel());
        vehicle.setVehicleDetails(updatedVehicle.getVehicleDetails());
        return vehicle;
    }
}
