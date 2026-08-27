package com.kasztelanic.carcare.web.rest;

import com.kasztelanic.carcare.web.rest.vm.LoginVm;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class JwtSessionIT extends AbstractSessionIT {

    @Test
    void authenticatesTheSeededUserAndReplaysTheResponseHeaderToken() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/authenticate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(LoginVm.of("user", "user"))))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.AUTHORIZATION, startsWith("Bearer ")))
            .andReturn();

        mockMvc.perform(get("/api/vehicle/all")
                .header(HttpHeaders.AUTHORIZATION, loginResult.getResponse().getHeader(HttpHeaders.AUTHORIZATION)))
            .andExpect(status().isOk());
    }

    @Test
    void permitsTheSpaRootButRejectsAnonymousProtectedApiRequests() throws Exception {
        mockMvc.perform(get("/"))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/vehicle/all"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }
}
