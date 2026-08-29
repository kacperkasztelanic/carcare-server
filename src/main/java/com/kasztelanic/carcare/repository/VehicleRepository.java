package com.kasztelanic.carcare.repository;

import com.kasztelanic.carcare.domain.Vehicle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    @Query("select vehicle from Vehicle vehicle where vehicle.owner.login = ?#{principal.username} and vehicle.archivedAt is null")
    List<Vehicle> findByOwnerIsCurrentUser();

    @Query("select vehicle from Vehicle vehicle where vehicle.id = :id and vehicle.owner.login = ?#{principal.username}")
    Optional<Vehicle> findByIdAndOwnerIsCurrentUser(@Param("id") Long id);

    // Result order feeds cost-en.json's index-exact array. H2 and MariaDB currently return
    // insertion order by luck, not contract; add "order by vehicle.id" if that assertion flakes.
    @Query("select vehicle from Vehicle vehicle where vehicle.id in :id and vehicle.owner.login = ?#{principal.username}")
    List<Vehicle> findAllByIdAndOwnerIsCurrentUser(@Param("id") Collection<Long> id);

    @Query("select vehicle from Vehicle vehicle where vehicle.id in :id and vehicle.owner.login = ?#{principal.username} and vehicle.archivedAt is null")
    List<Vehicle> findAllActiveByIdAndOwnerIsCurrentUser(@Param("id") Collection<Long> id);

    @Query("select vehicle from Vehicle vehicle " +
        "where vehicle.owner.login = ?#{principal.username} " +
        "and vehicle.archivedAt is not null " +
        "and (exists (select refuel.id from Refuel refuel " +
        "where refuel.vehicle = vehicle and refuel.vehicleEvent.date between :dateFrom and :dateTo) " +
        "or exists (select repair.id from Repair repair " +
        "where repair.vehicle = vehicle and repair.vehicleEvent.date between :dateFrom and :dateTo) " +
        "or exists (select routineService.id from RoutineService routineService " +
        "where routineService.vehicle = vehicle and routineService.vehicleEvent.date between :dateFrom and :dateTo) " +
        "or exists (select inspection.id from Inspection inspection " +
        "where inspection.vehicle = vehicle and inspection.vehicleEvent.date between :dateFrom and :dateTo) " +
        "or exists (select insurance.id from Insurance insurance " +
        "where insurance.vehicle = vehicle and insurance.vehicleEvent.date between :dateFrom and :dateTo)) " +
        "order by vehicle.id")
    List<Vehicle> findArchivedByOwnerIsCurrentUserWithEventsBetween(@Param("dateFrom") LocalDate dateFrom,
                                                                      @Param("dateTo") LocalDate dateTo);

    @Query("select count(vehicle) > 0 from Vehicle vehicle "
        + "where vehicle.owner.login = ?#{principal.username} and vehicle.archivedAt is not null")
    boolean existsArchivedByOwnerIsCurrentUser();

    // join fetch on owner: the admin DTO reads owner.login for every row, and the association is
    // EAGER, so without this each page row costs an extra select. Ordering is supplied by the
    // caller's Pageable (see AdminVehicleServiceImpl.ARCHIVED_SORT) rather than hardcoded here,
    // so a client-supplied sort is honoured instead of being silently appended as a tiebreaker.
    @Query(value = "select vehicle from Vehicle vehicle join fetch vehicle.owner "
        + "where vehicle.archivedAt is not null",
        countQuery = "select count(vehicle) from Vehicle vehicle where vehicle.archivedAt is not null")
    Page<Vehicle> findAllArchived(Pageable pageable);
}
