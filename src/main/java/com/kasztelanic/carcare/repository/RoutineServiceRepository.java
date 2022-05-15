package com.kasztelanic.carcare.repository;

import com.kasztelanic.carcare.domain.RoutineService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface RoutineServiceRepository extends JpaRepository<RoutineService, Long> {

    List<RoutineService> findByNextByDateIn(Collection<LocalDate> dates);

    @Query("select routineService from RoutineService routineService join routineService.vehicle where routineService.id = :id and routineService.vehicle.owner.login = ?#{principal.username}")
    Optional<RoutineService> findByIdAndOwnerIsCurrentUser(@Param("id") Long id);

    @Query("select routineService from RoutineService routineService join routineService.vehicle where routineService.vehicle.id = :vehicleId and routineService.vehicle.owner.login = ?#{principal.username} order by routineService.vehicleEvent.date desc")
    List<RoutineService> findByVehicleIdAndOwnerIsCurrentUser(@Param("vehicleId") Long vehicleId);

    @Query("select routineService from RoutineService routineService join routineService.vehicle where routineService.vehicle.id = :vehicleId order by routineService.vehicleEvent.date desc")
    List<RoutineService> findByVehicleId(@Param("vehicleId") Long vehicleId);
}
