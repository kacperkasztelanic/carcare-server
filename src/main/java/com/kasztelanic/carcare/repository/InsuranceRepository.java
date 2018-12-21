package com.kasztelanic.carcare.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.kasztelanic.carcare.domain.Insurance;

@Repository
public interface InsuranceRepository extends JpaRepository<Insurance, Long> {

    List<Insurance> findByValidThruIn(Collection<LocalDate> dates);

    @Query("select insurance from Insurance insurance join insurance.vehicle where insurance.id = :id and insurance.vehicle.owner.login = ?#{principal.username}")
    Optional<Insurance> findByIdAndOwnerIsCurrentUser(@Param("id") Long id);

    @Query("select insurance from Insurance insurance join insurance.vehicle where insurance.vehicle.id = :vehicleId and insurance.vehicle.owner.login = ?#{principal.username}")
    List<Insurance> findByVehicleIdAndOwnerIsCurrentUser(@Param("vehicleId") Long vehicleId);
}
