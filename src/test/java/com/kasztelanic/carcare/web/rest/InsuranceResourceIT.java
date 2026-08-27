package com.kasztelanic.carcare.web.rest;

import com.kasztelanic.carcare.domain.Insurance;
import com.kasztelanic.carcare.domain.Vehicle;
import com.kasztelanic.carcare.fixtures.SessionFixtures;
import com.kasztelanic.carcare.service.dto.InsuranceDto;
import com.kasztelanic.carcare.service.dto.InsuranceTypeDto;
import com.kasztelanic.carcare.service.dto.VehicleEventDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InsuranceResourceIT extends AbstractSessionIT {

    @Test
    @WithMockUser(username = "user")
    void createsInsuranceWithObjectWrappedTypeLocationAndAlert() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleFor("user");
        MvcResult result = mockMvc.perform(post("/api/insurance/{vehicleId}", vehicle.getId())
                .contentType(MediaType.APPLICATION_JSON).content(json(request("Created insurer", "Created number", "Created details"))))
            .andExpect(status().isCreated())
            .andExpect(header().string("X-carcareApp-alert", "carcareApp.insurance.created"))
            .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
            .isEqualTo("/api/insurance/" + vehicle.getId() + "/" + responseId(result));
    }

    @Test
    @WithMockUser(username = "user")
    void getsInsuranceWithVehicleId() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleFor("user");
        Insurance insurance = sessionFixtures.insuranceFor(vehicle);

        mockMvc.perform(get("/api/insurance/{id}", insurance.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(insurance.getId()))
            .andExpect(jsonPath("$.vehicleId").value(vehicle.getId()));
    }

    @Test
    @WithMockUser(username = "user")
    void listsInsurancesWithTotalCount() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleFor("user");
        Insurance insurance = sessionFixtures.insuranceFor(vehicle);

        mockMvc.perform(get("/api/insurance/all/{vehicleId}", vehicle.getId()))
            .andExpect(status().isOk())
            .andExpect(header().exists("X-Total-Count"))
            .andExpect(jsonPath("$[0].id").value(insurance.getId()));
    }

    @Test
    @WithMockUser(username = "user")
    void updatesInsuranceWithObjectWrappedTypeAndAlert() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleFor("user");
        Insurance insurance = sessionFixtures.insuranceFor(vehicle);

        mockMvc.perform(put("/api/insurance/{id}", insurance.getId())
                .contentType(MediaType.APPLICATION_JSON).content(json(request("Updated insurer", "Updated number", "Updated details"))))
            .andExpect(status().isOk())
            .andExpect(header().string("X-carcareApp-alert", "carcareApp.insurance.updated"))
            .andExpect(jsonPath("$.insurer").value("Updated insurer"));
    }

    @Test
    @WithMockUser(username = "user")
    void updatesInsuranceWithBareStringTypeAndAlert() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleFor("user");
        Insurance insurance = sessionFixtures.insuranceFor(vehicle);

        mockMvc.perform(put("/api/insurance/{id}", insurance.getId())
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"vehicleEvent":{"mileage":12000,"date":"2024-06-01"},"validFrom":"2024-06-01",
                    "validThru":"2025-06-01","costInCents":10000,"number":"Bare number","insurer":"Bare insurer",
                    "details":"Bare details","insuranceType":"fixture"}
                    """))
            .andExpect(status().isOk())
            .andExpect(header().string("X-carcareApp-alert", "carcareApp.insurance.updated"))
            .andExpect(jsonPath("$.insuranceType.type").value(SessionFixtures.DEFAULT_INSURANCE_TYPE));
    }

    @Test
    @WithMockUser(username = "user")
    void returnsProblemDetailForMissingInsuranceType() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleFor("user");

        mockMvc.perform(post("/api/insurance/{vehicleId}", vehicle.getId())
                .contentType(MediaType.APPLICATION_JSON).content(json(request("Missing insurer", "Missing number", "Missing details", null))))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.path").value("/api/insurance/" + vehicle.getId()))
            .andExpect(jsonPath("$.message").value("error.http.400"));
    }

    @Test
    @WithMockUser(username = "user")
    void returnsProblemDetailForUnknownInsuranceType() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleFor("user");

        mockMvc.perform(post("/api/insurance/{vehicleId}", vehicle.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request("Unknown insurer", "Unknown number", "Unknown details",
                    InsuranceTypeDto.of("missing-insurance", "Missing insurance")))))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.path").value("/api/insurance/" + vehicle.getId()))
            .andExpect(jsonPath("$.message").value("error.http.400"));
    }

    @Test
    @WithMockUser(username = "user")
    void deletesInsuranceAndReturnsDeletedDto() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleFor("user");
        Insurance insurance = sessionFixtures.insuranceFor(vehicle);

        mockMvc.perform(delete("/api/insurance/{id}", insurance.getId()))
            .andExpect(status().isOk())
            .andExpect(header().string("X-carcareApp-alert", "carcareApp.insurance.deleted"))
            .andExpect(jsonPath("$.id").value(insurance.getId()));
    }

    @Test
    @WithMockUser(username = "user")
    void returnsNotFoundForUnknownInsurance() throws Exception {
        mockMvc.perform(get("/api/insurance/{id}", Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    private static InsuranceDto request(String insurer, String number, String details) {
        return request(insurer, number, details,
            InsuranceTypeDto.of(SessionFixtures.DEFAULT_INSURANCE_TYPE, "Fixture insurance"));
    }

    private static InsuranceDto request(String insurer, String number, String details, InsuranceTypeDto insuranceType) {
        return InsuranceDto.builder().vehicleEvent(VehicleEventDto.of(12_000, java.time.LocalDate.of(2024, 6, 1)))
            .validFrom(java.time.LocalDate.of(2024, 6, 1)).validThru(java.time.LocalDate.of(2025, 6, 1))
            .costInCents(10_000).insurer(insurer).number(number).details(details)
            .insuranceType(insuranceType).build();
    }
}
