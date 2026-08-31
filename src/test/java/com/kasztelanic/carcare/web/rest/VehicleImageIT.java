package com.kasztelanic.carcare.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kasztelanic.carcare.fixtures.SessionFixtures;
import com.kasztelanic.carcare.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * First end-to-end coverage that a vehicle can be created and updated <em>with</em> an image
 * through the real filter chain, and that the bytes round-trip back to the client — the behaviour
 * all three slices in this change must preserve.
 */
@WithMockUser(username = "user")
class VehicleImageIT extends AbstractImageIT {

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsVehicleWithImageAndRoundTripsBytes() throws Exception {
        byte[] png = SessionFixtures.pngBytes();

        MvcResult created = mockMvc.perform(post("/api/vehicle")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(SessionFixtures.vehicleDtoWithImage("Image create", png, "image/png"))))
            .andExpect(status().isCreated())
            .andReturn();

        long id = responseId(created);
        String storedName = vehicleRepository.findById(id).orElseThrow()
            .getVehicleDetails().getImage();
        assertThat(storedName).endsWith(".png");
        assertThat(Files.exists(imagePath(storedName))).isTrue();

        JsonNode body = objectMapper.readTree(created.getResponse().getContentAsByteArray());
        assertThat(body.path("vehicleDetails").path("image").binaryValue()).isEqualTo(png);
        assertThat(body.path("vehicleDetails").path("imageContentType").asText()).isEqualTo("image/png");
    }

    @Test
    void replacingTheImageReturnsTheNewBytes() throws Exception {
        byte[] png = SessionFixtures.pngBytes();
        MvcResult created = mockMvc.perform(post("/api/vehicle")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(SessionFixtures.vehicleDtoWithImage("Image replace", png, "image/png"))))
            .andExpect(status().isCreated())
            .andReturn();
        long id = responseId(created);

        byte[] jpeg = SessionFixtures.jpegBytes();
        MvcResult updated = mockMvc.perform(put("/api/vehicle/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(SessionFixtures.vehicleDtoWithImage("Image replace", jpeg, "image/jpeg"))))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode body = objectMapper.readTree(updated.getResponse().getContentAsByteArray());
        assertThat(body.path("vehicleDetails").path("image").binaryValue()).isEqualTo(jpeg);
        assertThat(body.path("vehicleDetails").path("imageContentType").asText()).isEqualTo("image/jpeg");
    }
}
