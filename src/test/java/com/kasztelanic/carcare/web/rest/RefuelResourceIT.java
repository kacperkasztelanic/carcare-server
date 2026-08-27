package com.kasztelanic.carcare.web.rest;

import com.kasztelanic.carcare.domain.Refuel;
import com.kasztelanic.carcare.domain.Vehicle;
import com.kasztelanic.carcare.service.dto.RefuelDto;
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

class RefuelResourceIT extends AbstractSessionIT {

    @Test
    @WithMockUser(username = "user")
    void createsRefuelWithLocationAndAlert() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleFor("user");
        MvcResult result = mockMvc.perform(post("/api/refuel/{vehicleId}", vehicle.getId())
                .contentType(MediaType.APPLICATION_JSON).content(json(request("Created station"))))
            .andExpect(status().isCreated())
            .andExpect(header().string("X-carcareApp-alert", "carcareApp.refuel.created"))
            .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
            .isEqualTo("/api/refuel/" + vehicle.getId() + "/" + responseId(result));
    }

    @Test
    @WithMockUser(username = "user")
    void getsARefuelWithVehicleId() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleFor("user");
        Refuel refuel = sessionFixtures.refuelFor(vehicle);

        mockMvc.perform(get("/api/refuel/{id}", refuel.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(refuel.getId()))
            .andExpect(jsonPath("$.vehicleId").value(vehicle.getId()));
    }

    @Test
    @WithMockUser(username = "user")
    void listsRefuelsWithTotalCount() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleFor("user");
        Refuel refuel = sessionFixtures.refuelFor(vehicle);

        mockMvc.perform(get("/api/refuel/all/{vehicleId}", vehicle.getId()))
            .andExpect(status().isOk())
            .andExpect(header().exists("X-Total-Count"))
            .andExpect(jsonPath("$[0].id").value(refuel.getId()));
    }

    @Test
    @WithMockUser(username = "user")
    void updatesRefuelWithAlert() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleFor("user");
        Refuel refuel = sessionFixtures.refuelFor(vehicle);

        mockMvc.perform(put("/api/refuel/{id}", refuel.getId())
                .contentType(MediaType.APPLICATION_JSON).content(json(request("Updated station"))))
            .andExpect(status().isOk())
            .andExpect(header().string("X-carcareApp-alert", "carcareApp.refuel.updated"))
            .andExpect(jsonPath("$.station").value("Updated station"));
    }

    @Test
    @WithMockUser(username = "user")
    void deletesRefuelAndReturnsDeletedDto() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleFor("user");
        Refuel refuel = sessionFixtures.refuelFor(vehicle);

        mockMvc.perform(delete("/api/refuel/{id}", refuel.getId()))
            .andExpect(status().isOk())
            .andExpect(header().string("X-carcareApp-alert", "carcareApp.refuel.deleted"))
            .andExpect(jsonPath("$.id").value(refuel.getId()));
    }

    @Test
    @WithMockUser(username = "user")
    void returnsNotFoundForUnknownRefuel() throws Exception {
        mockMvc.perform(get("/api/refuel/{id}", Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    private static RefuelDto request(String station) {
        return RefuelDto.builder().vehicleEvent(VehicleEventDto.of(12_000, java.time.LocalDate.of(2024, 6, 1)))
            .costInCents(10_000).volume(42_000).station(station).build();
    }
}
