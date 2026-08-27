package com.kasztelanic.carcare.web.rest;

import com.kasztelanic.carcare.domain.Vehicle;
import com.kasztelanic.carcare.service.dto.PeriodVehicle;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.LocalDate;
import java.util.List;

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
}
