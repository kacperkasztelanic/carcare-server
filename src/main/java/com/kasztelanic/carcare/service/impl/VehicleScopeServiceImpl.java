package com.kasztelanic.carcare.service.impl;

import com.kasztelanic.carcare.domain.Vehicle;
import com.kasztelanic.carcare.repository.VehicleRepository;
import com.kasztelanic.carcare.service.VehicleScopeService;
import com.kasztelanic.carcare.service.exception.ArchivedResourceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class VehicleScopeServiceImpl implements VehicleScopeService {

    private final VehicleRepository vehicleRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<Vehicle> findActiveOwnedVehicle(Long id) {
        return vehicleRepository.findByIdAndOwnerIsCurrentUser(id)
            .map(this::requireActiveVehicle);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Vehicle> findActiveOwnedVehicles(Collection<Long> ids) {
        return vehicleRepository.findAllActiveByIdAndOwnerIsCurrentUser(ids);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Vehicle> findCostVehicles(Collection<Long> requestedIds, LocalDate dateFrom, LocalDate dateTo) {
        List<Vehicle> requestedVehicles = vehicleRepository.findAllByIdAndOwnerIsCurrentUser(requestedIds);
        List<Vehicle> archivedVehicles = vehicleRepository
            .findArchivedByOwnerIsCurrentUserWithEventsBetween(dateFrom, dateTo);
        Map<Long, Vehicle> vehiclesById = new LinkedHashMap<>();
        requestedVehicles.forEach(vehicle -> vehiclesById.put(vehicle.getId(), vehicle));
        archivedVehicles.forEach(vehicle -> vehiclesById.putIfAbsent(vehicle.getId(), vehicle));
        return new ArrayList<>(vehiclesById.values());
    }

    @Override
    public void assertActiveVehicle(Vehicle vehicle) {
        requireActiveVehicle(vehicle);
    }

    private Vehicle requireActiveVehicle(Vehicle vehicle) {
        if (vehicle.getArchivedAt() != null) {
            throw new ArchivedResourceException();
        }
        return vehicle;
    }
}
