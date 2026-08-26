package com.kasztelanic.carcare.web.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kasztelanic.carcare.CarcareApp;
import com.kasztelanic.carcare.fixtures.SessionFixtures;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Common full-context harness for the session-parity integration tests.
 */
@SpringBootTest(classes = CarcareApp.class)
@AutoConfigureMockMvc
@Transactional
public abstract class AbstractSessionIT {

    private static final ObjectMapper REQUEST_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected SessionFixtures sessionFixtures;

    protected byte[] json(Object value) throws JsonProcessingException {
        return REQUEST_MAPPER.writeValueAsBytes(value);
    }
}
