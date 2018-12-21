package com.kasztelanic.carcare.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kasztelanic.carcare.domain.Inspection;

public interface InspectionRepository extends JpaRepository<Inspection, Long> {

    List<Inspection> findByValidThruIn(Collection<LocalDate> dates);

    @Query("select inspection from Inspection inspection join inspection.vehicle where inspection.id = :id and inspection.vehicle.owner.login = ?#{principal.username}")
    Optional<Inspection> findByIdAndOwnerIsCurrentUser(@Param("id") Long id);

    @Query("select inspection from Inspection inspection join inspection.vehicle where inspection.vehicle.id = :vehicleId and inspection.vehicle.owner.login = ?#{principal.username}")
    List<Inspection> findByVehicleIdAndOwnerIsCurrentUser(@Param("vehicleId") Long vehicleId);
}
