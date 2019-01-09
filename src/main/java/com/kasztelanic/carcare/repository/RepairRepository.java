package com.kasztelanic.carcare.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kasztelanic.carcare.domain.Repair;

public interface RepairRepository extends JpaRepository<Repair, Long> {

    @Query("select repair from Repair repair join repair.vehicle where repair.id = :id and repair.vehicle.owner.login = ?#{principal.username}")
    Optional<Repair> findByIdAndOwnerIsCurrentUser(@Param("id") Long id);

    @Query("select repair from Repair repair join repair.vehicle where repair.vehicle.id = :vehicleId and repair.vehicle.owner.login = ?#{principal.username} order by repair.vehicleEvent.date desc")
    List<Repair> findByVehicleIdAndOwnerIsCurrentUser(@Param("vehicleId") Long vehicleId);

    @Query("select repair from Repair repair join repair.vehicle where repair.vehicle.id = :vehicleId order by repair.vehicleEvent.date desc")
    List<Repair> findByVehicleId(@Param("vehicleId") Long vehicleId);
}
