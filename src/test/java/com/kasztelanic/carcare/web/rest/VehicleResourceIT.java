package com.kasztelanic.carcare.web.rest;

import com.kasztelanic.carcare.domain.Vehicle;
import com.kasztelanic.carcare.fixtures.SessionFixtures;
import com.kasztelanic.carcare.service.dto.FuelTypeDto;
import com.kasztelanic.carcare.service.dto.VehicleDetailsDto;
import com.kasztelanic.carcare.service.dto.VehicleDto;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VehicleResourceIT extends AbstractSessionIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @WithMockUser(username = "user")
    void getsAnOwnedVehicle() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleFor("user");

        mockMvc.perform(get("/api/vehicle/{id}", vehicle.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(vehicle.getId()))
            .andExpect(jsonPath("$.make").value(vehicle.getMake()));
    }

    @Test
    @WithMockUser(username = "user")
    void listsOwnedVehiclesWithTotalCount() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleFor("user");

        mockMvc.perform(get("/api/vehicle/all"))
            .andExpect(status().isOk())
            .andExpect(header().exists("X-Total-Count"))
            .andExpect(jsonPath("$[?(@.id == %s)]", vehicle.getId()).exists());
    }

    @Test
    @WithMockUser(username = "user")
    void createsVehicleWithLocationAndAlert() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/vehicle")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(vehicleRequest("Created make"))))
            .andExpect(status().isCreated())
            .andExpect(header().string("X-carcareApp-alert", "carcareApp.vehicle.created"))
            .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo("/api/vehicle/" + responseId(result));
    }

    @Test
    @WithMockUser(username = "user")
    void returnsProblemDetailForMissingFuelType() throws Exception {
        mockMvc.perform(post("/api/vehicle")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(vehicleRequest("Missing fuel type", null))))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.path").value("/api/vehicle"))
            .andExpect(jsonPath("$.message").value("error.http.400"));
    }

    @Test
    @WithMockUser(username = "user")
    void returnsProblemDetailForUnknownFuelType() throws Exception {
        mockMvc.perform(post("/api/vehicle")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(vehicleRequest("Unknown fuel type", FuelTypeDto.of("missing-fuel", "Missing fuel")))))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.path").value("/api/vehicle"))
            .andExpect(jsonPath("$.message").value("error.http.400"));
    }

    @Test
    @WithMockUser(username = "user")
    void updatesAnOwnedVehicleWithAlert() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleFor("user");

        mockMvc.perform(put("/api/vehicle/{id}", vehicle.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(vehicleRequest("Updated make"))))
            .andExpect(status().isOk())
            .andExpect(header().string("X-carcareApp-alert", "carcareApp.vehicle.updated"))
            .andExpect(jsonPath("$.make").value("Updated make"));
    }

    @Test
    @WithMockUser(username = "user")
    void deletesAnEventFreeVehicleWithAlert() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleFor("user");

        mockMvc.perform(delete("/api/vehicle/{id}", vehicle.getId()))
            .andExpect(status().isOk())
            .andExpect(header().string("X-carcareApp-alert", "carcareApp.vehicle.deleted"))
            .andExpect(jsonPath("$.id").value(vehicle.getId()));

        mockMvc.perform(get("/api/vehicle/{id}", vehicle.getId()))
            .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "user")
    void returnsBodylessNotFoundForUnknownVehicle() throws Exception {
        mockMvc.perform(get("/api/vehicle/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound())
            .andExpect(content().string(""))
            .andExpect(header().doesNotExist(HttpHeaders.CONTENT_TYPE));
    }

    @Disabled("S-05 vehicle-archiving owns deleting vehicles with event history")
    @Test
    @WithMockUser(username = "user")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void deletingVehicleWithHistoryCurrentlyViolatesForeignKeys() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleWithEventsFor("user");
        try {
            mockMvc.perform(delete("/api/vehicle/{id}", vehicle.getId()))
                .andExpect(status().is5xxServerError());
        } finally {
            // This test runs outside the class-level transaction, so its rows are committed to the
            // JVM-wide H2 instance (DB_CLOSE_DELAY=-1) and must be removed by hand.
            purgeVehicle(vehicle.getId());
        }
    }

    private void purgeVehicle(Long vehicleId) {
        for (String table : new String[] { "refuels", "repairs", "routine_services", "inspections", "insurances" }) {
            jdbcTemplate.update("delete from " + table + " where vehicle_id = ?", vehicleId);
        }
        jdbcTemplate.update("delete from vehicles where id = ?", vehicleId);
    }

    private static VehicleDto vehicleRequest(String make) {
        return vehicleRequest(make, FuelTypeDto.of(SessionFixtures.DEFAULT_FUEL_TYPE, "Fixture fuel"));
    }

    private static VehicleDto vehicleRequest(String make, FuelTypeDto fuelType) {
        return VehicleDto.builder()
            .make(make)
            .model("Fixture model")
            .licensePlate("FX-POST")
            .fuelType(fuelType)
            .vehicleDetails(VehicleDetailsDto.defaultBuilder().build())
            .build();
    }
}
