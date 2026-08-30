package com.kasztelanic.carcare.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kasztelanic.carcare.CarcareApp;
import com.kasztelanic.carcare.domain.Vehicle;
import com.kasztelanic.carcare.fixtures.SessionFixtures;
import com.kasztelanic.carcare.repository.VehicleRepository;
import com.kasztelanic.carcare.security.AuthoritiesConstants;
import com.kasztelanic.carcare.service.EventService;
import com.kasztelanic.carcare.service.ImageStorageService;
import com.kasztelanic.carcare.service.dto.FuelTypeDto;
import com.kasztelanic.carcare.service.dto.VehicleDetailsDto;
import com.kasztelanic.carcare.service.dto.VehicleDto;
import com.kasztelanic.carcare.service.mapper.VehicleMapper;
import com.kasztelanic.carcare.web.filter.RequestBodyLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-context coverage for the request admission boundary.
 */
@SpringBootTest(classes = CarcareApp.class)
@AutoConfigureMockMvc
@Transactional
class RequestBodyLimitIT {

    private static final String EXISTING_IMAGE = "existing-image.png";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SessionFixtures sessionFixtures;

    @Autowired
    private VehicleRepository vehicleRepository;

    @SpyBean
    private EventService eventService;

    @SpyBean
    private ImageStorageService imageStorageService;

    @SpyBean
    private VehicleMapper vehicleMapper;

    @Test
    void rejectsOversizedAnonymousApiRequestBeforeSecurity() throws Exception {
        mockMvc.perform(post("/api/vehicle")
                .contentType(MediaType.APPLICATION_JSON)
                .content(oversizedVehicleJson()))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(content().encoding(StandardCharsets.UTF_8.name()))
            .andExpect(jsonPath("$.status").value(413))
            .andExpect(jsonPath("$.message").value("error.http.413"))
            .andExpect(jsonPath("$.path").value("/api/vehicle"))
            .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    @WithMockUser(username = "user")
    void rejectsOversizedValidJsonBeforeBodyControllerExecution() throws Exception {
        clearInvocations(eventService);

        mockMvc.perform(post("/api/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(oversizedEmptyArrayJson()))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.message").value("error.http.413"))
            .andExpect(jsonPath("$.path").value("/api/events"));

        verifyNoInteractions(eventService);
    }

    @Test
    @WithMockUser(username = "user")
    void oversizedVehicleCreateLeavesDatabaseAndImageStorageUntouched() throws Exception {
        int vehicleCountBefore = jdbcTemplate.queryForObject("select count(*) from vehicles", Integer.class);
        clearInvocations(imageStorageService, vehicleMapper);

        mockMvc.perform(post("/api/vehicle")
                .contentType(MediaType.APPLICATION_JSON)
                .content(oversizedVehicleJson()))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(header().doesNotExist("X-carcareApp-alert"));

        assertThat(jdbcTemplate.queryForObject("select count(*) from vehicles", Integer.class))
            .isEqualTo(vehicleCountBefore);
        verify(imageStorageService, never()).save(any(), any());
        verify(imageStorageService, never()).delete(any());
        verify(vehicleMapper, never()).vehicleDtoToVehicle(any());
    }

    @Test
    @WithMockUser(username = "user")
    void oversizedVehicleUpdatePreservesStateAndSkipsSideEffects() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleFor("user");
        vehicle.getVehicleDetails().setModelSuffix("existing suffix");
        vehicle.getVehicleDetails().setNotes("existing notes");
        vehicle.getVehicleDetails().setImage(EXISTING_IMAGE);
        vehicleRepository.saveAndFlush(vehicle);
        Map<String, Object> stateBefore = vehicleState(vehicle.getId());
        clearInvocations(imageStorageService, vehicleMapper);

        mockMvc.perform(put("/api/vehicle/{id}", vehicle.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(oversizedVehicleJson()))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(header().doesNotExist("X-carcareApp-alert"));

        assertThat(vehicleState(vehicle.getId())).isEqualTo(stateBefore);
        verify(imageStorageService, never()).save(any(), any());
        verify(imageStorageService, never()).delete(any());
        verify(vehicleMapper, never()).vehicleDtoToVehicle(any());
    }

    @Test
    @WithMockUser(username = "user")
    void smallVehicleJsonStillReachesTheController() throws Exception {
        mockMvc.perform(post("/api/vehicle")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(vehicleRequest("Small request"))))
            .andExpect(status().isCreated())
            .andExpect(header().string("X-carcareApp-alert", "carcareApp.vehicle.created"));
    }

    @Test
    void rootAndManagementRequestsRemainAvailable() throws Exception {
        mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(header().exists("Content-Security-Policy"));
        mockMvc.perform(get("/management/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/management/info"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activeProfiles").isArray());
    }

    @Test
    void ignoredOptionsRequestRemainsAvailable() throws Exception {
        mockMvc.perform(options("/api/vehicle")
                .header(HttpHeaders.ORIGIN, "http://localhost")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name()))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void bodylessPathOnlyPostRemainsAvailable() throws Exception {
        mockMvc.perform(post("/api/reminder-advance/{days}", 123))
            .andExpect(status().isCreated())
            .andExpect(header().string("X-carcareApp-alert", "carcareApp.reminder-advance.created"));
    }

    private Map<String, Object> vehicleState(Long vehicleId) {
        return jdbcTemplate.queryForMap("select * from vehicles where id = ?", vehicleId);
    }

    private byte[] oversizedEmptyArrayJson() {
        byte[] body = new byte[(int) RequestBodyLimitFilter.MAX_REQUEST_BODY_BYTES + 1];
        body[0] = '[';
        body[1] = ']';
        java.util.Arrays.fill(body, 2, body.length, (byte) ' ');
        return body;
    }

    private byte[] oversizedVehicleJson() throws JsonProcessingException {
        VehicleDetailsDto details = VehicleDetailsDto.defaultBuilder()
            .image(new byte[3_200_000])
            .imageContentType("image/png")
            .build();
        VehicleDto request = VehicleDto.builder()
            .make("Oversized make")
            .model("Oversized model")
            .licensePlate("FX-OVERSIZED")
            .fuelType(FuelTypeDto.of(SessionFixtures.DEFAULT_FUEL_TYPE, "Fixture fuel"))
            .vehicleDetails(details)
            .build();
        byte[] body = objectMapper.writeValueAsBytes(request);
        assertThat(body.length).isGreaterThan((int) RequestBodyLimitFilter.MAX_REQUEST_BODY_BYTES);
        return body;
    }

    private VehicleDto vehicleRequest(String make) {
        return VehicleDto.builder()
            .make(make)
            .model("Small model")
            .licensePlate("FX-SMALL")
            .fuelType(FuelTypeDto.of(SessionFixtures.DEFAULT_FUEL_TYPE, "Fixture fuel"))
            .vehicleDetails(VehicleDetailsDto.defaultBuilder().build())
            .build();
    }
}
