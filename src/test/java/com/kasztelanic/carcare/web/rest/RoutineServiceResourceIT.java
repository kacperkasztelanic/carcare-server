package com.kasztelanic.carcare.web.rest;

import com.kasztelanic.carcare.domain.RoutineService;
import com.kasztelanic.carcare.domain.Vehicle;
import com.kasztelanic.carcare.service.dto.RoutineServiceDto;
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

class RoutineServiceResourceIT extends AbstractSessionIT {

    @Test
    @WithMockUser(username = "user")
    void createsRoutineServiceWithLocationAndAlert() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleFor("user");
        MvcResult result = mockMvc.perform(post("/api/routine-service/{vehicleId}", vehicle.getId())
                .contentType(MediaType.APPLICATION_JSON).content(json(request("Created station", "Created details"))))
            .andExpect(status().isCreated())
            .andExpect(header().string("X-carcareApp-alert", "carcareApp.routineService.created"))
            .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
            .isEqualTo("/api/routine-service/" + vehicle.getId() + "/" + responseId(result));
    }

    @Test
    @WithMockUser(username = "user")
    void getsARoutineServiceWithVehicleId() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleFor("user");
        RoutineService routineService = sessionFixtures.routineServiceFor(vehicle);

        mockMvc.perform(get("/api/routine-service/{id}", routineService.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(routineService.getId()))
            .andExpect(jsonPath("$.vehicleId").value(vehicle.getId()));
    }

    @Test
    @WithMockUser(username = "user")
    void listsRoutineServicesWithTotalCount() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleFor("user");
        RoutineService routineService = sessionFixtures.routineServiceFor(vehicle);

        mockMvc.perform(get("/api/routine-service/all/{vehicleId}", vehicle.getId()))
            .andExpect(status().isOk())
            .andExpect(header().exists("X-Total-Count"))
            .andExpect(jsonPath("$[0].id").value(routineService.getId()));
    }

    @Test
    @WithMockUser(username = "user")
    void updatesRoutineServiceWithAlert() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleFor("user");
        RoutineService routineService = sessionFixtures.routineServiceFor(vehicle);

        mockMvc.perform(put("/api/routine-service/{id}", routineService.getId())
                .contentType(MediaType.APPLICATION_JSON).content(json(request("Updated station", "Updated details"))))
            .andExpect(status().isOk())
            .andExpect(header().string("X-carcareApp-alert", "carcareApp.routineService.updated"))
            .andExpect(jsonPath("$.details").value("Updated details"));
    }

    @Test
    @WithMockUser(username = "user")
    void deletesRoutineServiceAndReturnsDeletedDto() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleFor("user");
        RoutineService routineService = sessionFixtures.routineServiceFor(vehicle);

        mockMvc.perform(delete("/api/routine-service/{id}", routineService.getId()))
            .andExpect(status().isOk())
            .andExpect(header().string("X-carcareApp-alert", "carcareApp.routineService.deleted"))
            .andExpect(jsonPath("$.id").value(routineService.getId()));
    }

    @Test
    @WithMockUser(username = "user")
    void rejectsEveryRoutineServiceOperationForAnArchivedVehicle() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleFor("user");
        RoutineService routineService = sessionFixtures.routineServiceFor(vehicle);
        mockMvc.perform(delete("/api/vehicle/{id}", vehicle.getId())).andExpect(status().isOk());

        mockMvc.perform(get("/api/routine-service/{id}", routineService.getId())).andExpect(status().isGone());
        mockMvc.perform(get("/api/routine-service/all/{vehicleId}", vehicle.getId())).andExpect(status().isGone());
        mockMvc.perform(post("/api/routine-service/{vehicleId}", vehicle.getId())
                .contentType(MediaType.APPLICATION_JSON).content(json(request("Archived station", "Archived details"))))
            .andExpect(status().isGone());
        mockMvc.perform(put("/api/routine-service/{id}", routineService.getId())
                .contentType(MediaType.APPLICATION_JSON).content(json(request("Archived station", "Archived details"))))
            .andExpect(status().isGone());
        mockMvc.perform(delete("/api/routine-service/{id}", routineService.getId())).andExpect(status().isGone());
    }

    @Test
    @WithMockUser(username = "user")
    void returnsNotFoundForUnknownRoutineService() throws Exception {
        mockMvc.perform(get("/api/routine-service/{id}", Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    private static RoutineServiceDto request(String station, String details) {
        return RoutineServiceDto.builder().vehicleEvent(VehicleEventDto.of(12_000, java.time.LocalDate.of(2024, 6, 1)))
            .costInCents(10_000).nextByMileage(20_000).nextByDate(java.time.LocalDate.of(2025, 6, 1))
            .station(station).details(details).build();
    }
}
