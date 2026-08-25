package com.kasztelanic.carcare.config;

import com.kasztelanic.carcare.CarcareApp;
import com.kasztelanic.carcare.web.rest.errors.ErrorConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Runtime smoke matrix for the Spring Security 6 migration in {@link SecurityConfiguration}.
 * Complements the anonymous/USER/ADMIN matrix already covered by {@code AuditResourceIT} and
 * {@code UserResourceIT} rather than repeating it here.
 */
@SpringBootTest(classes = CarcareApp.class)
@AutoConfigureMockMvc
class SecurityConfigurationIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void spaRootIsPubliclyServedWithSecurityHeaders() throws Exception {
        mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(header().exists("Content-Security-Policy"))
            .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
            .andExpect(header().exists("Permissions-Policy"))
            .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    @Test
    void privateApiRequestAsAnonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/account"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(content().encoding("UTF-8"))
            .andExpect(jsonPath("$." + ErrorConstants.MESSAGE_KEY).value("error.http.401"))
            .andExpect(jsonPath("$." + ErrorConstants.PATH_KEY).value("/api/account"));
    }

    @Test
    void managementHealthStaysPubliclyAccessibleAndHealthy() throws Exception {
        mockMvc.perform(get("/management/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }
}
