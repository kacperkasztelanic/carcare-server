package com.kasztelanic.carcare.web.rest;

import com.kasztelanic.carcare.domain.Vehicle;
import com.kasztelanic.carcare.repository.FuelTypeRepository;
import com.kasztelanic.carcare.repository.InsuranceTypeRepository;
import com.kasztelanic.carcare.repository.VehicleRepository;
import com.kasztelanic.carcare.security.AuthoritiesConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Integration contracts for the admin-only test-data endpoints. */
class TestDataResourceIT extends AbstractSessionIT {

    @Autowired
    private FuelTypeRepository fuelTypeRepository;

    @Autowired
    private InsuranceTypeRepository insuranceTypeRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Test
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanPopulateFuelTypes() throws Exception {
        long countBefore = fuelTypeRepository.count();

        mockMvc.perform(get("/api/test-data/populate-fuel-types"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().string("true"));

        assertThat(fuelTypeRepository.count()).isEqualTo(countBefore + 7);
    }

    @Test
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanPopulateInsuranceTypes() throws Exception {
        long countBefore = insuranceTypeRepository.count();

        mockMvc.perform(get("/api/test-data/populate-insurance-types"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().string("true"));

        assertThat(insuranceTypeRepository.count()).isEqualTo(countBefore + 3);
    }

    @Test
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanGenerateOneRandomVehicleForTheCurrentUser() throws Exception {
        Set<Long> vehicleIdsBefore = new HashSet<>();
        vehicleRepository.findAll().forEach(vehicle -> vehicleIdsBefore.add(vehicle.getId()));

        mockMvc.perform(get("/api/test-data/populate-fuel-types"))
            .andExpect(status().isOk())
            .andExpect(content().string("true"));

        mockMvc.perform(get("/api/test-data/random-vehicles/1"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().string("true"));

        vehicleRepository.flush();
        assertThat(vehicleRepository.count()).isEqualTo(vehicleIdsBefore.size() + 1);
        List<Vehicle> generatedVehicles = vehicleRepository.findAll().stream()
            .filter(vehicle -> !vehicleIdsBefore.contains(vehicle.getId()))
            .collect(Collectors.toList());
        assertThat(generatedVehicles).hasSize(1);
        Vehicle generatedVehicle = generatedVehicles.get(0);
        assertThat(generatedVehicle.getOwner().getLogin()).isEqualTo("admin");
    }

    @Test
    @WithMockUser(username = "user", authorities = AuthoritiesConstants.USER)
    void userCannotAccessTestDataEndpoints() throws Exception {
        long fuelTypeCount = fuelTypeRepository.count();
        long insuranceTypeCount = insuranceTypeRepository.count();
        long vehicleCount = vehicleRepository.count();

        mockMvc.perform(get("/api/test-data/populate-fuel-types"))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/test-data/populate-insurance-types"))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/test-data/random-vehicles/1"))
            .andExpect(status().isForbidden());

        assertThat(fuelTypeRepository.count()).isEqualTo(fuelTypeCount);
        assertThat(insuranceTypeRepository.count()).isEqualTo(insuranceTypeCount);
        assertThat(vehicleRepository.count()).isEqualTo(vehicleCount);
    }

    @Test
    @WithAnonymousUser
    void anonymousCannotAccessTestDataEndpoints() throws Exception {
        mockMvc.perform(get("/api/test-data/populate-fuel-types"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/test-data/populate-insurance-types"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/test-data/random-vehicles/1"))
            .andExpect(status().isUnauthorized());
    }
}
