package com.kasztelanic.carcare.web.rest;

import com.kasztelanic.carcare.domain.PersistentAuditEvent;
import com.kasztelanic.carcare.domain.Vehicle;
import com.kasztelanic.carcare.repository.InspectionRepository;
import com.kasztelanic.carcare.repository.InsuranceRepository;
import com.kasztelanic.carcare.repository.PersistenceAuditEventRepository;
import com.kasztelanic.carcare.repository.RefuelRepository;
import com.kasztelanic.carcare.repository.RepairRepository;
import com.kasztelanic.carcare.repository.RoutineServiceRepository;
import com.kasztelanic.carcare.repository.VehicleRepository;
import com.kasztelanic.carcare.security.AuthoritiesConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Commit-path truth for the admin purge (Phase 2): committed row removal across the vehicle and all
 * five event tables, the after-commit image-file deletion, and the in-transaction
 * {@code VEHICLE_PURGED} audit event. A class-{@code @Transactional} test cannot observe any of it —
 * a rolled-back transaction never runs {@code afterCompletion} with {@code STATUS_COMMITTED}.
 */
@WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
class AdminVehiclePurgeIT extends AbstractImageIT {

    @Autowired
    private VehicleRepository vehicleRepository;
    @Autowired
    private RefuelRepository refuelRepository;
    @Autowired
    private RepairRepository repairRepository;
    @Autowired
    private RoutineServiceRepository routineServiceRepository;
    @Autowired
    private InspectionRepository inspectionRepository;
    @Autowired
    private InsuranceRepository insuranceRepository;
    @Autowired
    private PersistenceAuditEventRepository persistenceAuditEventRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void purgesArchivedVehicleWithEventsImageAndWritesAuditEvent() throws Exception {
        Long id = null;
        String image = null;
        try {
            Vehicle vehicle = sessionFixtures.vehicleWithEventsFor("user");
            id = vehicle.getId();
            vehicle = sessionFixtures.imageFor(vehicle, "fake-png-bytes".getBytes());
            image = vehicle.getVehicleDetails().getImage();
            vehicle = sessionFixtures.archive(vehicle, Instant.parse("2024-06-01T00:00:00Z"));
            final Long vehicleId = id;
            Path imagePath = imagePath(image);
            assertThat(Files.exists(imagePath)).isTrue();

            mockMvc.perform(delete("/api/admin/vehicles/{id}/purge", vehicleId))
                .andExpect(status().isNoContent())
                .andExpect(header().string("X-carcareApp-alert", "carcareApp.vehicle.deleted"))
                .andExpect(header().string("X-carcareApp-params", vehicleId.toString()));

            assertThat(vehicleRepository.findById(vehicleId)).isEmpty();
            assertThat(refuelRepository.findByVehicleId(vehicleId)).isEmpty();
            assertThat(repairRepository.findByVehicleId(vehicleId)).isEmpty();
            assertThat(routineServiceRepository.findByVehicleId(vehicleId)).isEmpty();
            assertThat(inspectionRepository.findByVehicleId(vehicleId)).isEmpty();
            assertThat(insuranceRepository.findByVehicleId(vehicleId)).isEmpty();
            assertThat(Files.exists(imagePath)).isFalse();

            // Read the audit event and its @ElementCollection data map inside a transaction — the
            // repository call otherwise returns detached entities and getData() would throw.
            new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
                PersistentAuditEvent audit = persistenceAuditEventRepository.findByPrincipal("admin").stream()
                    .filter(e -> "VEHICLE_PURGED".equals(e.getAuditEventType()))
                    .filter(e -> vehicleId.toString().equals(e.getData().get("vehicleId")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no VEHICLE_PURGED audit event for vehicle " + vehicleId));
                assertThat(audit.getData().get("ownerLogin")).isEqualTo("user");
                assertThat(audit.getData().get("refuels")).isEqualTo("1");
            });
        } finally {
            if (id != null) {
                sessionFixtures.purgeRowsFor(List.of(id));
                deletePurgeAuditEvents();
            }
            if (image != null) {
                Files.deleteIfExists(imagePath(image));
            }
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void purgingAnActiveVehicleIsRejectedWith409() throws Exception {
        Long id = null;
        try {
            Vehicle vehicle = sessionFixtures.vehicleWithEventsFor("user");
            id = vehicle.getId();
            mockMvc.perform(delete("/api/admin/vehicles/{id}/purge", id))
                .andExpect(status().isConflict());
            assertThat(vehicleRepository.findById(id)).isPresent();
            assertThat(refuelRepository.findByVehicleId(id)).isNotEmpty();
        } finally {
            if (id != null) {
                sessionFixtures.purgeRowsFor(List.of(id));
            }
        }
    }

    @Test
    void purgingAnUnknownIdReturns404() throws Exception {
        mockMvc.perform(delete("/api/admin/vehicles/{id}/purge", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    void purgingAsNonAdminReturns403() throws Exception {
        mockMvc.perform(delete("/api/admin/vehicles/{id}/purge", 1L).with(user("user")))
            .andExpect(status().isForbidden());
    }

    private void deletePurgeAuditEvents() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.update("delete from jhi_persistent_audit_evt_data where event_id in "
                + "(select event_id from jhi_persistent_audit_event where event_type = 'VEHICLE_PURGED')");
            jdbcTemplate.update("delete from jhi_persistent_audit_event where event_type = 'VEHICLE_PURGED'");
        });
    }
}
