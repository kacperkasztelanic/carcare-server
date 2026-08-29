package com.kasztelanic.carcare.repository;

import com.kasztelanic.carcare.domain.Vehicle;
import com.kasztelanic.carcare.fixtures.SessionFixtures;
import com.kasztelanic.carcare.web.rest.AbstractSessionIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class VehicleArchivingRepositoryIT extends AbstractSessionIT {

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private InsuranceRepository insuranceRepository;

    @Autowired
    private InspectionRepository inspectionRepository;

    @Autowired
    private RoutineServiceRepository routineServiceRepository;

    @Test
    @WithMockUser(username = "user")
    void separatesActiveHistoricalAndAdministrativeVehiclePolicies() {
        Vehicle active = sessionFixtures.vehicleFor("user");
        Vehicle archived = sessionFixtures.archive(sessionFixtures.vehicleFor("user"), Instant.parse("2026-08-28T10:00:00Z"));
        Vehicle foreignArchived = sessionFixtures.archive(sessionFixtures.vehicleFor("admin"), Instant.parse("2026-08-29T10:00:00Z"));

        assertThat(vehicleRepository.findByOwnerIsCurrentUser())
            .extracting(Vehicle::getId)
            .containsExactly(active.getId());
        assertThat(vehicleRepository.findByIdAndOwnerIsCurrentUser(archived.getId()))
            .contains(archived);
        assertThat(vehicleRepository.findAllByIdAndOwnerIsCurrentUser(
            List.of(active.getId(), archived.getId(), foreignArchived.getId())))
            .extracting(Vehicle::getId)
            .containsExactlyInAnyOrder(active.getId(), archived.getId());
        assertThat(vehicleRepository.findAllActiveByIdAndOwnerIsCurrentUser(
            List.of(active.getId(), archived.getId(), foreignArchived.getId())))
            .extracting(Vehicle::getId)
            .containsExactly(active.getId());
        // findAllArchived filters and pages but imposes no default order; the "archivedAt desc, id
        // asc" default belongs to AdminVehicleServiceImpl and is pinned by AdminVehicleResourceIT.
        assertThat(vehicleRepository.findAllArchived(PageRequest.of(0, 10)).getContent())
            .extracting(Vehicle::getId)
            .containsExactlyInAnyOrder(foreignArchived.getId(), archived.getId());
        assertThat(vehicleRepository.findAllArchived(PageRequest.of(0, 10,
                Sort.by(Sort.Order.desc("archivedAt"), Sort.Order.asc("id")))).getContent())
            .extracting(Vehicle::getId)
            .containsExactly(foreignArchived.getId(), archived.getId());
    }

    @Test
    @WithMockUser(username = "user")
    void findsArchivedVehiclesForInclusiveEventDateBoundariesAndCurrentOwner() {
        LocalDate dateFrom = LocalDate.of(2024, 1, 10);
        LocalDate dateTo = LocalDate.of(2024, 5, 10);

        Vehicle refuelVehicle = sessionFixtures.archive(sessionFixtures.vehicleFor("user"), Instant.parse("2026-08-28T10:00:00Z"));
        sessionFixtures.refuelFor(refuelVehicle, 1_000, dateFrom);
        Vehicle repairVehicle = sessionFixtures.archive(sessionFixtures.vehicleFor("user"), Instant.parse("2026-08-28T10:01:00Z"));
        sessionFixtures.repairFor(repairVehicle, 1_100, LocalDate.of(2024, 2, 10), 100);
        Vehicle routineVehicle = sessionFixtures.archive(sessionFixtures.vehicleFor("user"), Instant.parse("2026-08-28T10:02:00Z"));
        sessionFixtures.routineServiceFor(routineVehicle, 1_200, LocalDate.of(2024, 3, 10), 100,
            2_000, LocalDate.of(2025, 3, 10));
        Vehicle inspectionVehicle = sessionFixtures.archive(sessionFixtures.vehicleFor("user"), Instant.parse("2026-08-28T10:03:00Z"));
        sessionFixtures.inspectionFor(inspectionVehicle, 1_300, LocalDate.of(2024, 4, 10), 100, dateTo);
        Vehicle insuranceVehicle = sessionFixtures.archive(sessionFixtures.vehicleFor("user"), Instant.parse("2026-08-28T10:04:00Z"));
        sessionFixtures.insuranceFor(insuranceVehicle, 1_400, dateTo, dateFrom, dateTo, 100);

        Vehicle beforePeriod = sessionFixtures.archive(sessionFixtures.vehicleFor("user"), Instant.parse("2026-08-28T10:05:00Z"));
        sessionFixtures.refuelFor(beforePeriod, 1_500, dateFrom.minusDays(1));
        Vehicle afterPeriod = sessionFixtures.archive(sessionFixtures.vehicleFor("user"), Instant.parse("2026-08-28T10:07:00Z"));
        sessionFixtures.refuelFor(afterPeriod, 1_800, dateTo.plusDays(1));
        Vehicle foreign = sessionFixtures.archive(sessionFixtures.vehicleFor("admin"), Instant.parse("2026-08-28T10:06:00Z"));
        sessionFixtures.refuelFor(foreign, 1_600, dateFrom);
        Vehicle active = sessionFixtures.vehicleFor("user");
        sessionFixtures.refuelFor(active, 1_700, dateFrom);

        assertThat(vehicleRepository.findArchivedByOwnerIsCurrentUserWithEventsBetween(dateFrom, dateTo))
            .extracting(Vehicle::getId)
            .containsExactly(refuelVehicle.getId(), repairVehicle.getId(), routineVehicle.getId(),
                inspectionVehicle.getId(), insuranceVehicle.getId());
    }

    @Test
    @WithMockUser(username = "user")
    void reminderQueriesExcludeEventsBelongingToArchivedVehicles() {
        LocalDate reminderDate = LocalDate.of(2026, 9, 1);
        Vehicle active = sessionFixtures.vehicleFor("user");
        Vehicle archived = sessionFixtures.archive(sessionFixtures.vehicleFor("user"), Instant.parse("2026-08-28T10:00:00Z"));

        sessionFixtures.insuranceFor(active, 1_000, LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1),
            reminderDate, 100);
        sessionFixtures.insuranceFor(archived, 1_100, LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1),
            reminderDate, 100);
        sessionFixtures.inspectionFor(active, 1_000, LocalDate.of(2024, 1, 1), 100, reminderDate);
        sessionFixtures.inspectionFor(archived, 1_100, LocalDate.of(2024, 1, 1), 100, reminderDate);
        sessionFixtures.routineServiceFor(active, 1_000, LocalDate.of(2024, 1, 1), 100, 2_000, reminderDate);
        sessionFixtures.routineServiceFor(archived, 1_100, LocalDate.of(2024, 1, 1), 100, 2_000, reminderDate);

        assertThat(insuranceRepository.findByValidThruIn(Set.of(reminderDate)))
            .extracting(insurance -> insurance.getVehicle().getId())
            .containsExactly(active.getId());
        assertThat(inspectionRepository.findByValidThruIn(Set.of(reminderDate)))
            .extracting(inspection -> inspection.getVehicle().getId())
            .containsExactly(active.getId());
        assertThat(routineServiceRepository.findByNextByDateIn(Set.of(reminderDate)))
            .extracting(service -> service.getVehicle().getId())
            .containsExactly(active.getId());
    }
}
