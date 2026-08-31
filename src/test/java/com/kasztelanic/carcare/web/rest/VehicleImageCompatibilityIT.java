package com.kasztelanic.carcare.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kasztelanic.carcare.domain.Vehicle;
import com.kasztelanic.carcare.fixtures.SessionFixtures;
import com.kasztelanic.carcare.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.test.context.support.WithMockUser;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression coverage for the nine filenames inventoried on the production volume. The fixture
 * uses anonymized, format-valid bytes rather than committing production photos; the contract under
 * test is that every persisted name, including the four legacy {@code .bin} names, remains loadable
 * and keeps the filename-derived response content type.
 */
@WithMockUser(username = "user")
class VehicleImageCompatibilityIT extends AbstractImageIT {

    private static final List<LegacyImage> LEGACY_IMAGES = List.of(
        new LegacyImage("8eaf8e93-fc9a-4a90-a4c8-fe9d5395d63a.bin", "application/octet-stream", Format.PNG, true),
        new LegacyImage("9b73df1e-edd5-4293-81e0-4a2616b35294.bin", "application/octet-stream", Format.PNG, true),
        new LegacyImage("e233f90c-9e64-4b3b-90b7-2640da35897a.bin", "application/octet-stream", Format.PNG, true),
        new LegacyImage("7a705225-6cca-4f26-9f08-e72bd3896144.bin", "application/octet-stream", Format.PNG, true),
        new LegacyImage("3f2d692c-865e-4854-bd43-3b4969fa1490.png", "image/png", Format.PNG, false),
        new LegacyImage("e8d28af0-a9b9-441a-b95d-75110d480a35.jpg", "image/jpeg", Format.JPEG, false),
        new LegacyImage("adea3b21-595d-4826-8276-898749fbf18c.jpg", "image/jpeg", Format.JPEG, false),
        new LegacyImage("7f4dea20-4057-4e28-8af5-2260ce462be4.jpg", "image/jpeg", Format.JPEG, false),
        new LegacyImage("883a7d3c-0540-4a96-9c94-2dd4eec517e2.jpg", "image/jpeg", Format.JPEG, false)
    );

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void documentedProductionImageNamesRemainLoadable() throws Exception {
        assertThat(LEGACY_IMAGES).hasSize(9);

        byte[] placeholder;
        try (InputStream input = new ClassPathResource("default.png").getInputStream()) {
            placeholder = input.readAllBytes();
        }
        byte[] png = SessionFixtures.pngBytes();
        byte[] jpeg = SessionFixtures.jpegBytes();

        try {
            for (LegacyImage fixture : LEGACY_IMAGES) {
                byte[] bytes = switch (fixture.format()) {
                    case PNG -> fixture.placeholder() ? placeholder : png;
                    case JPEG -> jpeg;
                };
                Vehicle vehicle = sessionFixtures.vehicleFor("user");
                Path path = imagePath(fixture.name());
                Files.write(path, bytes);
                vehicle.getVehicleDetails().setImage(fixture.name());
                vehicleRepository.saveAndFlush(vehicle);

                JsonNode body = objectMapper.readTree(mockMvc.perform(get("/api/vehicle/{id}", vehicle.getId()))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray());
                assertThat(body.path("vehicleDetails").path("image").binaryValue()).isEqualTo(bytes);
                assertThat(body.path("vehicleDetails").path("imageContentType").asText())
                    .isEqualTo(fixture.expectedContentType());
            }
        } finally {
            for (LegacyImage fixture : LEGACY_IMAGES) {
                Files.deleteIfExists(imagePath(fixture.name()));
            }
        }
    }

    private enum Format {
        PNG,
        JPEG
    }

    private record LegacyImage(String name, String expectedContentType, Format format, boolean placeholder) {
    }
}
