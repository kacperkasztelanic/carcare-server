package com.kasztelanic.carcare.web.rest;

import com.kasztelanic.carcare.domain.Vehicle;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

import java.time.Instant;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.hamcrest.Matchers.containsString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminVehicleResourceIT extends AbstractSessionIT {


    @Test
    void listsArchivedVehiclesWithDeterministicPaginationAndAdminDto() throws Exception {
        Vehicle older = archivedVehicle("2026-08-28T10:00:00Z");
        Vehicle newer = archivedVehicle("2026-08-28T11:00:00Z");
        Vehicle active = sessionFixtures.vehicleFor("user");

        mockMvc.perform(get("/api/admin/vehicles/archived")
                .param("page", "0").param("size", "1")
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(header().string("X-Total-Count", "2"))
            .andExpect(header().string("Link", containsString("page=1&size=1")))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(newer.getId()))
            .andExpect(jsonPath("$[0].ownerLogin").value("user"))
            .andExpect(jsonPath("$[0].make").value(newer.getMake()))
            .andExpect(jsonPath("$[0].model").value(newer.getModel()))
            .andExpect(jsonPath("$[0].licensePlate").value(newer.getLicensePlate()))
            .andExpect(jsonPath("$[0].archivedAt").value("2026-08-28T11:00:00Z"));

        // Keep the active fixture in the setup so the endpoint also proves it is excluded.
        assertThat(active.getArchivedAt()).isNull();
        assertThat(older.getArchivedAt()).isNotNull();
    }

    @Test
    void restoresArchivedVehicleAndReturnsTheAdminDto() throws Exception {
        Vehicle vehicle = archivedVehicle("2026-08-28T12:00:00Z");

        mockMvc.perform(put("/api/admin/vehicles/{id}/restore", vehicle.getId()).with(admin()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(vehicle.getId()))
            .andExpect(jsonPath("$.ownerLogin").value("user"))
            .andExpect(jsonPath("$.archivedAt").value(org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(get("/api/vehicle/{id}", vehicle.getId()).with(user("user")))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/vehicles/archived").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == %s)]", vehicle.getId()).doesNotExist());
    }

    @Test
    void restoringAnActiveVehicleIsAnIdempotentSuccess() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleFor("user");

        mockMvc.perform(put("/api/admin/vehicles/{id}/restore", vehicle.getId()).with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(vehicle.getId()))
            .andExpect(jsonPath("$.archivedAt").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void returnsNotFoundForUnknownRestoreTarget() throws Exception {
        mockMvc.perform(put("/api/admin/vehicles/{id}/restore", Long.MAX_VALUE).with(admin()))
            .andExpect(status().isNotFound());
    }

    @Test
    void normalUsersCannotListOrRestoreArchivedVehicles() throws Exception {
        Vehicle vehicle = archivedVehicle("2026-08-28T13:00:00Z");
        SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor normalUser = user("user");

        mockMvc.perform(get("/api/admin/vehicles/archived").with(normalUser))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/vehicles/{id}/restore", vehicle.getId()).with(user("user")))
            .andExpect(status().isForbidden());
    }

    private Vehicle archivedVehicle(String archivedAt) {
        return sessionFixtures.archivedVehicleFor("user", Instant.parse(archivedAt));
    }

    private static SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor admin() {
        return user("admin").roles("ADMIN");
    }
}
