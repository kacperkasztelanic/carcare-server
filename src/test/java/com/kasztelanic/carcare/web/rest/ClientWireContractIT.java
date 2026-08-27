package com.kasztelanic.carcare.web.rest;

import com.kasztelanic.carcare.domain.Inspection;
import com.kasztelanic.carcare.domain.Insurance;
import com.kasztelanic.carcare.domain.Refuel;
import com.kasztelanic.carcare.domain.Repair;
import com.kasztelanic.carcare.domain.RoutineService;
import com.kasztelanic.carcare.domain.Vehicle;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contracts used by client 1.2.5. The Bearer prefix is deliberately covered by JwtSessionIT in
 * Phase 5 because it mints the real authentication response.
 */
class ClientWireContractIT extends AbstractSessionIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    @WithMockUser(username = "user")
    void vehicleGetKeepsEmptyTrimmableStringsNonNullOnTheResponseSide() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleFor("user");
        entityManager.flush();
        jdbcTemplate.update("""
            update vehicles set make = '', model = '', license_plate = '', model_suffix = '', vin_number = '',
            vehicle_card = '', registration_certificate = '', notes = '' where id = ?
            """, vehicle.getId());
        entityManager.clear();

        // This pins GET responses only; null request fields are the separate Phase 7 mapper defect.
        mockMvc.perform(get("/api/vehicle/{id}", vehicle.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.make").isEmpty()).andExpect(jsonPath("$.model").isEmpty())
            .andExpect(jsonPath("$.licensePlate").isEmpty())
            .andExpect(jsonPath("$.vehicleDetails.modelSuffix").isEmpty())
            .andExpect(jsonPath("$.vehicleDetails.vinNumber").isEmpty())
            .andExpect(jsonPath("$.vehicleDetails.vehicleCard").isEmpty())
            .andExpect(jsonPath("$.vehicleDetails.registrationCertificate").isEmpty())
            .andExpect(jsonPath("$.vehicleDetails.notes").isEmpty());
    }

    @Test
    @WithMockUser(username = "user")
    void eventGetsKeepEmptyTrimmableStringsNonNullOnTheResponseSide() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleFor("user");
        Refuel refuel = sessionFixtures.refuelFor(vehicle);
        Repair repair = sessionFixtures.repairFor(vehicle);
        RoutineService routineService = sessionFixtures.routineServiceFor(vehicle);
        Inspection inspection = sessionFixtures.inspectionFor(vehicle);
        Insurance insurance = sessionFixtures.insuranceFor(vehicle);
        entityManager.flush();
        jdbcTemplate.update("update refuels set station = '' where id = ?", refuel.getId());
        jdbcTemplate.update("update repairs set station = '', details = '' where id = ?", repair.getId());
        jdbcTemplate.update("update routine_services set station = '', details = '' where id = ?", routineService.getId());
        jdbcTemplate.update("update inspections set station = '', details = '' where id = ?", inspection.getId());
        jdbcTemplate.update("update insurances set number = '', insurer = '', details = '' where id = ?", insurance.getId());
        entityManager.clear();

        assertEmptyEventStrings("/api/refuel/{id}", refuel.getId(), "station");
        assertEmptyEventStrings("/api/repair/{id}", repair.getId(), "station", "details");
        assertEmptyEventStrings("/api/routine-service/{id}", routineService.getId(), "station", "details");
        assertEmptyEventStrings("/api/inspection/{id}", inspection.getId(), "station", "details");
        assertEmptyEventStrings("/api/insurance/{id}", insurance.getId(), "number", "insurer", "details");
    }

    @Test
    @WithMockUser(username = "user")
    void everyEventGetIncludesVehicleIdForTheDeleteFlow() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleFor("user");
        Refuel refuel = sessionFixtures.refuelFor(vehicle);
        Repair repair = sessionFixtures.repairFor(vehicle);
        RoutineService routineService = sessionFixtures.routineServiceFor(vehicle);
        Inspection inspection = sessionFixtures.inspectionFor(vehicle);
        Insurance insurance = sessionFixtures.insuranceFor(vehicle);

        mockMvc.perform(get("/api/refuel/{id}", refuel.getId())).andExpect(jsonPath("$.vehicleId").value(vehicle.getId()));
        mockMvc.perform(get("/api/repair/{id}", repair.getId())).andExpect(jsonPath("$.vehicleId").value(vehicle.getId()));
        mockMvc.perform(get("/api/routine-service/{id}", routineService.getId())).andExpect(jsonPath("$.vehicleId").value(vehicle.getId()));
        mockMvc.perform(get("/api/inspection/{id}", inspection.getId())).andExpect(jsonPath("$.vehicleId").value(vehicle.getId()));
        mockMvc.perform(get("/api/insurance/{id}", insurance.getId())).andExpect(jsonPath("$.vehicleId").value(vehicle.getId()));
    }

    @Test
    @WithMockUser(username = "user")
    void vehicleAndEventListsExposeTotalCount() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleFor("user");
        sessionFixtures.refuelFor(vehicle);

        mockMvc.perform(get("/api/vehicle/all")).andExpect(status().isOk()).andExpect(header().exists("X-Total-Count"));
        mockMvc.perform(get("/api/refuel/all/{vehicleId}", vehicle.getId()))
            .andExpect(status().isOk()).andExpect(header().exists("X-Total-Count"));
    }

    private void assertEmptyEventStrings(String getPath, Long id, String... fields) throws Exception {
        var response = mockMvc.perform(get(getPath, id)).andExpect(status().isOk());
        for (String field : fields) {
            response.andExpect(jsonPath("$." + field).isEmpty());
        }
    }
}
