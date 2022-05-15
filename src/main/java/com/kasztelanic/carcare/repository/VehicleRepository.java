package com.kasztelanic.carcare.repository;

import com.kasztelanic.carcare.domain.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    @Query("select vehicle from Vehicle vehicle where vehicle.owner.login = ?#{principal.username}")
    List<Vehicle> findByOwnerIsCurrentUser();

    @Query("select vehicle from Vehicle vehicle where vehicle.id = :id and vehicle.owner.login = ?#{principal.username}")
    Optional<Vehicle> findByIdAndOwnerIsCurrentUser(@Param("id") Long id);

    @Query("select vehicle from Vehicle vehicle where vehicle.id in :id and vehicle.owner.login = ?#{principal.username}")
    List<Vehicle> findAllByIdAndOwnerIsCurrentUser(@Param("id") Collection<Long> id);
}
