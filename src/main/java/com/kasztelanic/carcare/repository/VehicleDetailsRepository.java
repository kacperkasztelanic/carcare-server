package com.kasztelanic.carcare.repository;

import com.kasztelanic.carcare.domain.VehicleDetails;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for the VehicleDetails entity.
 */
@Repository
public interface VehicleDetailsRepository extends JpaRepository<VehicleDetails, Long> {

}
