package com.kasztelanic.carcare.web.rest;

import com.kasztelanic.carcare.domain.Vehicle;
import com.kasztelanic.carcare.fixtures.SessionFixtures;
import com.kasztelanic.carcare.service.dto.FuelTypeDto;
import com.kasztelanic.carcare.service.dto.VehicleDetailsDto;
import com.kasztelanic.carcare.service.dto.VehicleDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
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
    void createsAndUpdatesVehicleWithTwentyCharacterLicensePlate() throws Exception {
        String createdLicensePlate = "ABCDEFGHIJKLMNOPQRST";
        String updatedLicensePlate = "12345678901234567890";

        MvcResult createResult = mockMvc.perform(post("/api/vehicle")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(vehicleRequestWithLicensePlate("Long plate create", createdLicensePlate))))
            .andExpect(status().isCreated())
            .andExpect(header().string("X-carcareApp-alert", "carcareApp.vehicle.created"))
            .andExpect(jsonPath("$.licensePlate").value(createdLicensePlate))
            .andReturn();

        mockMvc.perform(put("/api/vehicle/{id}", responseId(createResult))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(vehicleRequestWithLicensePlate("Long plate update", updatedLicensePlate))))
            .andExpect(status().isOk())
            .andExpect(header().string("X-carcareApp-alert", "carcareApp.vehicle.updated"))
            .andExpect(jsonPath("$.licensePlate").value(updatedLicensePlate));
    }

    @Test
    @WithMockUser(username = "user")
    void rejectsLicensePlateLongerThanTwentyCharacters() throws Exception {
        mockMvc.perform(post("/api/vehicle")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(vehicleRequestWithLicensePlate("Over-long plate", "ABCDEFGHIJKLMNOPQRSTU"))))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.path").value("/api/vehicle"))
            .andExpect(jsonPath("$.message").value("error.validation"))
            .andExpect(jsonPath("$.fieldErrors[0].field").value("licensePlate"))
            .andExpect(jsonPath("$.fieldErrors[0].message").value("Length"));
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
            .andExpect(status().isGone());
    }

    @Test
    @WithMockUser(username = "user")
    void returnsBodylessNotFoundForUnknownVehicle() throws Exception {
        mockMvc.perform(get("/api/vehicle/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound())
            .andExpect(content().string(""))
            .andExpect(header().doesNotExist(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    @WithMockUser(username = "user")
    void archivesVehicleWithHistoryAndReturnsGoneForDirectAccess() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleWithEventsFor("user");
        mockMvc.perform(delete("/api/vehicle/{id}", vehicle.getId()))
            .andExpect(status().isOk())
            .andExpect(header().string("X-carcareApp-alert", "carcareApp.vehicle.deleted"))
            .andExpect(jsonPath("$.id").value(vehicle.getId()));

        mockMvc.perform(get("/api/vehicle/{id}", vehicle.getId()))
            .andExpect(status().isGone())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.message").value("error.http.410"));
        mockMvc.perform(delete("/api/vehicle/{id}", vehicle.getId()))
            .andExpect(status().isGone());

        assertThat(jdbcTemplate.queryForObject("select count(*) from vehicles where id = ?", Integer.class,
            vehicle.getId())).isEqualTo(1);
    }

    private static VehicleDto vehicleRequest(String make) {
        return vehicleRequest(make, FuelTypeDto.of(SessionFixtures.DEFAULT_FUEL_TYPE, "Fixture fuel"));
    }

    private static VehicleDto vehicleRequestWithLicensePlate(String make, String licensePlate) {
        return vehicleRequest(make, licensePlate, FuelTypeDto.of(SessionFixtures.DEFAULT_FUEL_TYPE, "Fixture fuel"));
    }

    private static VehicleDto vehicleRequest(String make, FuelTypeDto fuelType) {
        return vehicleRequest(make, "FX-POST", fuelType);
    }

    private static VehicleDto vehicleRequest(String make, String licensePlate, FuelTypeDto fuelType) {
        return VehicleDto.builder()
            .make(make)
            .model("Fixture model")
            .licensePlate(licensePlate)
            .fuelType(fuelType)
            .vehicleDetails(VehicleDetailsDto.defaultBuilder().build())
            .build();
    }
}
