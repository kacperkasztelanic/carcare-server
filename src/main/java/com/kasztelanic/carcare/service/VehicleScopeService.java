package com.kasztelanic.carcare.service;

import com.kasztelanic.carcare.domain.Vehicle;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VehicleScopeService {

    Optional<Vehicle> findActiveOwnedVehicle(Long id);

    List<Vehicle> findActiveOwnedVehicles(Collection<Long> ids);

    List<Vehicle> findCostVehicles(Collection<Long> requestedIds, LocalDate dateFrom, LocalDate dateTo);

    void assertActiveVehicle(Vehicle vehicle);
}
