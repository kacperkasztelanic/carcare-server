package com.kasztelanic.carcare.web.rest;

import com.kasztelanic.carcare.domain.Inspection;
import com.kasztelanic.carcare.domain.Vehicle;
import com.kasztelanic.carcare.service.dto.InspectionDto;
import com.kasztelanic.carcare.service.dto.VehicleEventDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InspectionResourceIT extends AbstractSessionIT {

    @Test
    @WithMockUser(username = "user")
    void createsInspectionWithLocationAndAlert() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleFor("user");
        MvcResult result = mockMvc.perform(post("/api/inspection/{vehicleId}", vehicle.getId())
                .contentType(MediaType.APPLICATION_JSON).content(json(request("Created station", "Created details"))))
            .andExpect(status().isCreated())
            .andExpect(header().string("X-carcareApp-alert", "carcareApp.inspection.created"))
            .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
            .isEqualTo("/api/inspection/" + vehicle.getId() + "/" + responseId(result));
    }

    @Test
    @WithMockUser(username = "user")
    void getsAnInspectionWithVehicleId() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleFor("user");
        Inspection inspection = sessionFixtures.inspectionFor(vehicle);

        mockMvc.perform(get("/api/inspection/{id}", inspection.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(inspection.getId()))
            .andExpect(jsonPath("$.vehicleId").value(vehicle.getId()));
    }

    @Test
    @WithMockUser(username = "user")
    void listsInspectionsWithTotalCount() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleFor("user");
        Inspection inspection = sessionFixtures.inspectionFor(vehicle);

        mockMvc.perform(get("/api/inspection/all/{vehicleId}", vehicle.getId()))
            .andExpect(status().isOk())
            .andExpect(header().exists("X-Total-Count"))
            .andExpect(jsonPath("$[0].id").value(inspection.getId()));
    }

    @Test
    @WithMockUser(username = "user")
    void updatesInspectionWithAlert() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleFor("user");
        Inspection inspection = sessionFixtures.inspectionFor(vehicle);

        mockMvc.perform(put("/api/inspection/{id}", inspection.getId())
                .contentType(MediaType.APPLICATION_JSON).content(json(request("Updated station", "Updated details"))))
            .andExpect(status().isOk())
            .andExpect(header().string("X-carcareApp-alert", "carcareApp.inspection.updated"))
            .andExpect(jsonPath("$.details").value("Updated details"));
    }

    @Test
    @WithMockUser(username = "user")
    void deletesInspectionAndReturnsDeletedDto() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleFor("user");
        Inspection inspection = sessionFixtures.inspectionFor(vehicle);

        mockMvc.perform(delete("/api/inspection/{id}", inspection.getId()))
            .andExpect(status().isOk())
            .andExpect(header().string("X-carcareApp-alert", "carcareApp.inspection.deleted"))
            .andExpect(jsonPath("$.id").value(inspection.getId()));
    }

    @Test
    @WithMockUser(username = "user")
    void returnsNotFoundForUnknownInspection() throws Exception {
        mockMvc.perform(get("/api/inspection/{id}", Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    private static InspectionDto request(String station, String details) {
        return InspectionDto.builder().vehicleEvent(VehicleEventDto.of(12_000, java.time.LocalDate.of(2024, 6, 1)))
            .costInCents(10_000).station(station).validThru(java.time.LocalDate.of(2025, 6, 1)).details(details).build();
    }
}
