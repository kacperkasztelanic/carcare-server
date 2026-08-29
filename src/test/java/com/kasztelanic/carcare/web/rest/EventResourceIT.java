package com.kasztelanic.carcare.web.rest;

import com.kasztelanic.carcare.domain.Vehicle;
import com.kasztelanic.carcare.service.dto.PeriodVehicle;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EventResourceIT extends AbstractSessionIT {

    @Test
    @WithMockUser(username = "user")
    void keepsTheFirstPeriodWhenVehicleIdIsRepeated() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleWithEventsFor("user");
        PeriodVehicle firstPeriod = PeriodVehicle.of(vehicle.getId(), LocalDate.of(2024, 1, 1), LocalDate.of(2025, 12, 31));
        PeriodVehicle ignoredPeriod = PeriodVehicle.of(vehicle.getId(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        mockMvc.perform(post("/api/events")
                .contentType(MediaType.APPLICATION_JSON).content(json(List.of(firstPeriod, ignoredPeriod))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    @WithMockUser(username = "user")
    void omitsArchivedVehiclesAndKeepsEmptyCompositeResponseSuccessful() throws Exception {
        Vehicle active = sessionFixtures.vehicleWithEventsFor("user");
        Vehicle archived = sessionFixtures.vehicleWithEventsFor("user");

        mockMvc.perform(delete("/api/vehicle/{id}", archived.getId()))
            .andExpect(status().isOk());

        PeriodVehicle activePeriod = PeriodVehicle.of(active.getId(), LocalDate.of(2024, 1, 1),
            LocalDate.of(2025, 12, 31));
        PeriodVehicle archivedPeriod = PeriodVehicle.of(archived.getId(), LocalDate.of(2024, 1, 1),
            LocalDate.of(2025, 12, 31));

        mockMvc.perform(post("/api/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(List.of(activePeriod, archivedPeriod))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].vehicleId").value(active.getId()))
            .andExpect(jsonPath("$[1].vehicleId").value(active.getId()))
            .andExpect(jsonPath("$[2].vehicleId").value(active.getId()));

        mockMvc.perform(post("/api/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(List.of(archivedPeriod))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }
}
