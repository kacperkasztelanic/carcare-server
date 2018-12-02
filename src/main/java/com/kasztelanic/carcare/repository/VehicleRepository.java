package com.kasztelanic.carcare.repository;

import com.kasztelanic.carcare.domain.Vehicle;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data repository for the Vehicle entity.
 */
@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    @Query("select vehicle from Vehicle vehicle where vehicle.owner.login = ?#{principal.username}")
    List<Vehicle> findByOwnerIsCurrentUser();
}
