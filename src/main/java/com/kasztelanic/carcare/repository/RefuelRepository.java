package com.kasztelanic.carcare.repository;

import com.kasztelanic.carcare.domain.Refuel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefuelRepository extends JpaRepository<Refuel, Long> {

    @Query("select refuel from Refuel refuel join refuel.vehicle where refuel.id = :id and refuel.vehicle.owner.login = ?#{principal.username}")
    Optional<Refuel> findByIdAndOwnerIsCurrentUser(@Param("id") Long id);

    @Query("select refuel from Refuel refuel join refuel.vehicle where refuel.vehicle.id = :vehicleId and refuel.vehicle.owner.login = ?#{principal.username} order by refuel.vehicleEvent.date desc")
    List<Refuel> findByVehicleIdAndOwnerIsCurrentUser(@Param("vehicleId") Long vehicleId);

    @Query("select refuel from Refuel refuel join refuel.vehicle where refuel.vehicle.id = :vehicleId order by refuel.vehicleEvent.date desc")
    List<Refuel> findByVehicleId(@Param("vehicleId") Long vehicleId);
}
