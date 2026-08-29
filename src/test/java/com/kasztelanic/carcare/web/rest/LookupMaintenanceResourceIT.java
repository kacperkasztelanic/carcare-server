package com.kasztelanic.carcare.web.rest;

import com.kasztelanic.carcare.domain.FuelType;
import com.kasztelanic.carcare.domain.InsuranceType;
import com.kasztelanic.carcare.domain.ReminderAdvance;
import com.kasztelanic.carcare.domain.Vehicle;
import com.kasztelanic.carcare.fixtures.SessionFixtures;
import com.kasztelanic.carcare.repository.FuelTypeRepository;
import com.kasztelanic.carcare.repository.InsuranceTypeRepository;
import com.kasztelanic.carcare.repository.ReminderAdvanceRepository;
import com.kasztelanic.carcare.security.AuthoritiesConstants;
import com.kasztelanic.carcare.service.dto.FuelTypeRequest;
import com.kasztelanic.carcare.service.dto.InsuranceTypeRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LookupMaintenanceResourceIT extends AbstractSessionIT {

    private static final String FUEL_TYPE = "PHASE2-FUEL";
    private static final String INSURANCE_TYPE = "P2-INSURE";
    private static final int REMINDER_DAYS = 42;

    @Autowired
    private FuelTypeRepository fuelTypeRepository;
    @Autowired
    private InsuranceTypeRepository insuranceTypeRepository;
    @Autowired
    private ReminderAdvanceRepository reminderAdvanceRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void getsEnglishLookupResponsesWithCounts() throws Exception {
        mockMvc.perform(get("/api/fuel-type"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Total-Count", String.valueOf(fuelTypeRepository.count())))
            .andExpect(jsonPath("$[?(@.type == 'fixture-fuel')].translation").value(hasItem("Fixture fuel")));

        mockMvc.perform(get("/api/insurance-type"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Total-Count", String.valueOf(insuranceTypeRepository.count())))
            .andExpect(jsonPath("$[?(@.type == 'fixture')].translation").value(hasItem("Fixture insurance")));

        mockMvc.perform(get("/api/reminder-advance"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Total-Count", String.valueOf(reminderAdvanceRepository.count())))
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    @WithMockUser(username = "user", authorities = AuthoritiesConstants.USER)
    void getsPolishLookupResponses() throws Exception {
        sessionFixtures.seedGoldenDataset();

        mockMvc.perform(get("/api/fuel-type"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.type == 'fixture-fuel')].translation").value(hasItem("Paliwo testowe")));

        mockMvc.perform(get("/api/insurance-type"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.type == 'fixture')].translation").value(hasItem("Ubezpieczenie testowe")));
    }

    @Test
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void createsFuelInsuranceAndReminderWithCanonicalLocationsAndAlerts() throws Exception {
        mockMvc.perform(post("/api/fuel-type")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(FuelTypeRequest.of("phase2-fuel", "Phase 2 fuel", "Paliwo fazy 2"))))
            .andExpect(status().isCreated())
            .andExpect(header().string(HttpHeaders.LOCATION, "/api/fuel-type/" + FUEL_TYPE))
            .andExpect(header().string("X-carcareApp-alert", "carcareApp.fuel-type.created"))
            .andExpect(header().string("X-carcareApp-params", FUEL_TYPE))
            .andExpect(jsonPath("$").value(FUEL_TYPE));
        assertThat(fuelTypeRepository.findByType(FUEL_TYPE)).isPresent();

        mockMvc.perform(post("/api/insurance-type")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(InsuranceTypeRequest.of("p2-insure", "Phase 2 insurance", "Ubezpieczenie fazy 2"))))
            .andExpect(status().isCreated())
            .andExpect(header().string(HttpHeaders.LOCATION, "/api/insurance-type/" + INSURANCE_TYPE))
            .andExpect(header().string("X-carcareApp-alert", "carcareApp.insurance-type.created"))
            .andExpect(header().string("X-carcareApp-params", INSURANCE_TYPE))
            .andExpect(jsonPath("$").value(INSURANCE_TYPE));
        assertThat(insuranceTypeRepository.findByType(INSURANCE_TYPE)).isPresent();

        mockMvc.perform(post("/api/reminder-advance/{days}", REMINDER_DAYS))
            .andExpect(status().isCreated())
            .andExpect(header().string(HttpHeaders.LOCATION, "/api/reminder-advance/" + REMINDER_DAYS))
            .andExpect(header().string("X-carcareApp-alert", "carcareApp.reminder-advance.created"))
            .andExpect(header().string("X-carcareApp-params", String.valueOf(REMINDER_DAYS)))
            .andExpect(jsonPath("$").value(REMINDER_DAYS));
        assertThat(reminderAdvanceRepository.findByDays(REMINDER_DAYS)).isPresent();
    }

    @Test
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void deletesFuelInsuranceAndReminderAndReturnsNotFoundWhenAbsent() throws Exception {
        fuelTypeRepository.saveAndFlush(FuelType.of(FUEL_TYPE, "Phase 2 fuel", "Paliwo fazy 2"));
        insuranceTypeRepository.saveAndFlush(InsuranceType.of(INSURANCE_TYPE, "Phase 2 insurance", "Ubezpieczenie fazy 2"));
        reminderAdvanceRepository.saveAndFlush(ReminderAdvance.of(REMINDER_DAYS));

        mockMvc.perform(delete("/api/fuel-type/{type}", FUEL_TYPE))
            .andExpect(status().isOk())
            .andExpect(header().string("X-carcareApp-alert", "carcareApp.fuel-type.deleted"))
            .andExpect(header().string("X-carcareApp-params", FUEL_TYPE))
            .andExpect(content().string(""));
        mockMvc.perform(delete("/api/fuel-type/{type}", FUEL_TYPE))
            .andExpect(status().isNotFound())
            .andExpect(content().string(""));
        assertThat(fuelTypeRepository.findByType(FUEL_TYPE)).isEmpty();

        mockMvc.perform(delete("/api/insurance-type/{type}", INSURANCE_TYPE))
            .andExpect(status().isOk())
            .andExpect(header().string("X-carcareApp-alert", "carcareApp.insurance-type.deleted"))
            .andExpect(header().string("X-carcareApp-params", INSURANCE_TYPE))
            .andExpect(content().string(""));
        mockMvc.perform(delete("/api/insurance-type/{type}", INSURANCE_TYPE))
            .andExpect(status().isNotFound())
            .andExpect(content().string(""));
        assertThat(insuranceTypeRepository.findByType(INSURANCE_TYPE)).isEmpty();

        mockMvc.perform(delete("/api/reminder-advance/{days}", REMINDER_DAYS))
            .andExpect(status().isOk())
            .andExpect(header().string("X-carcareApp-alert", "carcareApp.reminder-advance.deleted"))
            .andExpect(header().string("X-carcareApp-params", String.valueOf(REMINDER_DAYS)))
            .andExpect(content().string(""));
        mockMvc.perform(delete("/api/reminder-advance/{days}", REMINDER_DAYS))
            .andExpect(status().isNotFound())
            .andExpect(content().string(""));
        assertThat(reminderAdvanceRepository.findByDays(REMINDER_DAYS)).isEmpty();
    }

    @Test
    @WithMockUser(username = "user", authorities = AuthoritiesConstants.USER)
    void rejectsEveryMutationForUsersWithoutChangingCounts() throws Exception {
        long fuelCount = fuelTypeRepository.count();
        long insuranceCount = insuranceTypeRepository.count();
        long reminderCount = reminderAdvanceRepository.count();

        mockMvc.perform(post("/api/fuel-type")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(FuelTypeRequest.of(FUEL_TYPE, "Phase 2 fuel", "Paliwo fazy 2"))))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/fuel-type/{type}", "fixture-fuel"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/insurance-type")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(InsuranceTypeRequest.of("p2-insure", "Phase 2 insurance", "Ubezpieczenie fazy 2"))))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/insurance-type/{type}", "fixture"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/reminder-advance/{days}", REMINDER_DAYS))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/reminder-advance/{days}", 3))
            .andExpect(status().isForbidden());

        assertThat(fuelTypeRepository.count()).isEqualTo(fuelCount);
        assertThat(insuranceTypeRepository.count()).isEqualTo(insuranceCount);
        assertThat(reminderAdvanceRepository.count()).isEqualTo(reminderCount);
    }

    @Test
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void deletingAnInUseFuelTypeReturns409AndRollsBack() throws Exception {
        String type = "INUSE-FUEL-" + System.nanoTime();
        FuelType fuelType = fuelTypeRepository.saveAndFlush(FuelType.of(type, "In use", "W użyciu"));
        Vehicle vehicle = sessionFixtures.vehicleFor("user", fuelType);
        try {
            mockMvc.perform(delete("/api/fuel-type/{type}", type))
                .andExpect(status().isConflict());
            // The in-use FK aborted the delete: the row is still there.
            assertThat(fuelTypeRepository.findByType(type)).isPresent();
        } finally {
            // hikari auto-commit=false: raw JDBC cleanup must run inside a transaction.
            // FK-safe order: vehicle rows first, then the dedicated fuel-type row.
            new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
                jdbcTemplate.update("delete from vehicles where id = ?", vehicle.getId());
                jdbcTemplate.update("delete from fuel_types where type = ?", type);
            });
        }
    }

    @Test
    @WithAnonymousUser
    void rejectsAnonymousLookupAndMutationRequests() throws Exception {
        mockMvc.perform(get("/api/fuel-type")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/insurance-type")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/reminder-advance")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/reminder-advance/{days}", REMINDER_DAYS))
            .andExpect(status().isUnauthorized());
    }
}
