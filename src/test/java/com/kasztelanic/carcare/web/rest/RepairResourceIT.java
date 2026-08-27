package com.kasztelanic.carcare.web.rest;

import com.kasztelanic.carcare.domain.Repair;
import com.kasztelanic.carcare.domain.Vehicle;
import com.kasztelanic.carcare.service.dto.RepairDto;
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

class RepairResourceIT extends AbstractSessionIT {

    @Test
    @WithMockUser(username = "user")
    void createsRepairWithLocationAndAlert() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleFor("user");
        MvcResult result = mockMvc.perform(post("/api/repair/{vehicleId}", vehicle.getId())
                .contentType(MediaType.APPLICATION_JSON).content(json(request("Created station", "Created details"))))
            .andExpect(status().isCreated())
            .andExpect(header().string("X-carcareApp-alert", "carcareApp.repair.created"))
            .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
            .isEqualTo("/api/repair/" + vehicle.getId() + "/" + responseId(result));
    }

    @Test
    @WithMockUser(username = "user")
    void getsARepairWithVehicleId() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleFor("user");
        Repair repair = sessionFixtures.repairFor(vehicle);

        mockMvc.perform(get("/api/repair/{id}", repair.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(repair.getId()))
            .andExpect(jsonPath("$.vehicleId").value(vehicle.getId()));
    }

    @Test
    @WithMockUser(username = "user")
    void listsRepairsWithTotalCount() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleFor("user");
        Repair repair = sessionFixtures.repairFor(vehicle);

        mockMvc.perform(get("/api/repair/all/{vehicleId}", vehicle.getId()))
            .andExpect(status().isOk())
            .andExpect(header().exists("X-Total-Count"))
            .andExpect(jsonPath("$[0].id").value(repair.getId()));
    }

    @Test
    @WithMockUser(username = "user")
    void updatesRepairWithAlert() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleFor("user");
        Repair repair = sessionFixtures.repairFor(vehicle);

        mockMvc.perform(put("/api/repair/{id}", repair.getId())
                .contentType(MediaType.APPLICATION_JSON).content(json(request("Updated station", "Updated details"))))
            .andExpect(status().isOk())
            .andExpect(header().string("X-carcareApp-alert", "carcareApp.repair.updated"))
            .andExpect(jsonPath("$.details").value("Updated details"));
    }

    @Test
    @WithMockUser(username = "user")
    void deletesRepairAndReturnsDeletedDto() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleFor("user");
        Repair repair = sessionFixtures.repairFor(vehicle);

        mockMvc.perform(delete("/api/repair/{id}", repair.getId()))
            .andExpect(status().isOk())
            .andExpect(header().string("X-carcareApp-alert", "carcareApp.repair.deleted"))
            .andExpect(jsonPath("$.id").value(repair.getId()));
    }

    @Test
    @WithMockUser(username = "user")
    void returnsNotFoundForUnknownRepair() throws Exception {
        mockMvc.perform(get("/api/repair/{id}", Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    private static RepairDto request(String station, String details) {
        return RepairDto.builder().vehicleEvent(VehicleEventDto.of(12_000, java.time.LocalDate.of(2024, 6, 1)))
            .costInCents(10_000).station(station).details(details).build();
    }
}
