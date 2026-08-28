package com.kasztelanic.carcare.web.rest;

import com.kasztelanic.carcare.CarcareApp;
import com.kasztelanic.carcare.domain.PersistentAuditEvent;
import com.kasztelanic.carcare.repository.PersistenceAuditEventRepository;
import com.kasztelanic.carcare.security.AuthoritiesConstants;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the {@link AuditResource} REST controller.
 */
@SpringBootTest(classes = CarcareApp.class)
@AutoConfigureMockMvc
@Transactional
@WithMockUser(authorities = AuthoritiesConstants.ADMIN)
class AuditResourceIT {

    private static final String SAMPLE_PRINCIPAL = "SAMPLE_PRINCIPAL";
    private static final String SAMPLE_TYPE = "SAMPLE_TYPE";
    private static final Instant SAMPLE_TIMESTAMP = Instant.parse("2015-08-04T10:11:30Z");
    private static final long SECONDS_PER_DAY = 60 * 60 * 24;

    @Autowired
    private PersistenceAuditEventRepository auditEventRepository;
    @Autowired
    private MockMvc restAuditMockMvc;

    private PersistentAuditEvent auditEvent;

    @BeforeEach
    void initTest() {
        auditEventRepository.deleteAll();
        auditEvent = new PersistentAuditEvent();
        auditEvent.setAuditEventType(SAMPLE_TYPE);
        auditEvent.setPrincipal(SAMPLE_PRINCIPAL);
        auditEvent.setAuditEventDate(SAMPLE_TIMESTAMP);
        auditEvent.setData(Map.of("remoteAddress", "127.0.0.1", "sessionId", "session", "sample", "value"));
    }

    @Test
    void getAllAudits() throws Exception {
        // Initialize the database
        auditEventRepository.save(auditEvent);

        // Get all the audits
        restAuditMockMvc.perform(get("/management/audits"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].principal").value(hasItem(SAMPLE_PRINCIPAL)))
            .andExpect(jsonPath("$[0].timestamp").value(SAMPLE_TIMESTAMP.toString()))
            .andExpect(jsonPath("$[0].type").value(SAMPLE_TYPE))
            .andExpect(jsonPath("$[0].data.remoteAddress").value("127.0.0.1"))
            .andExpect(jsonPath("$[0].data.sessionId").value("session"))
            .andExpect(jsonPath("$[0].data.sample").value("value"));
    }

    @Test
    void getAudit() throws Exception {
        // Initialize the database
        auditEventRepository.save(auditEvent);

        // Get the audit
        restAuditMockMvc.perform(get("/management/audits/{id}", auditEvent.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.timestamp").value(SAMPLE_TIMESTAMP.toString()))
            .andExpect(jsonPath("$.principal").value(SAMPLE_PRINCIPAL))
            .andExpect(jsonPath("$.type").value(SAMPLE_TYPE))
            .andExpect(jsonPath("$.data.remoteAddress").value("127.0.0.1"))
            .andExpect(jsonPath("$.data.sessionId").value("session"))
            .andExpect(jsonPath("$.data.sample").value("value"));
    }

    @Test
    void getAuditsByDate() throws Exception {
        // Initialize the database
        auditEventRepository.save(auditEvent);

        // Generate dates for selecting audits by date, making sure the period will contain the audit
        String fromDate = SAMPLE_TIMESTAMP.minusSeconds(SECONDS_PER_DAY).toString().substring(0, 10);
        String toDate = SAMPLE_TIMESTAMP.plusSeconds(SECONDS_PER_DAY).toString().substring(0, 10);

        // Get the audit
        restAuditMockMvc.perform(get("/management/audits?fromDate="+fromDate+"&toDate="+toDate))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].principal").value(hasItem(SAMPLE_PRINCIPAL)))
            .andExpect(jsonPath("$[0].timestamp").value(SAMPLE_TIMESTAMP.toString()))
            .andExpect(jsonPath("$[0].type").value(SAMPLE_TYPE))
            .andExpect(jsonPath("$[0].data.sample").value("value"));
    }

    @Test
    void getNonExistingAuditsByDate() throws Exception {
        // Initialize the database
        auditEventRepository.save(auditEvent);

        // Generate dates for selecting audits by date, making sure the period will not contain the sample audit
        String fromDate  = SAMPLE_TIMESTAMP.minusSeconds(2*SECONDS_PER_DAY).toString().substring(0, 10);
        String toDate = SAMPLE_TIMESTAMP.minusSeconds(SECONDS_PER_DAY).toString().substring(0, 10);

        // Query audits but expect no results
        restAuditMockMvc.perform(get("/management/audits?fromDate=" + fromDate + "&toDate=" + toDate))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$").isEmpty())
            .andExpect(header().string("X-Total-Count", "0"))
            .andExpect(header().string("Link", containsString("page=0")))
            .andExpect(header().string("Link", containsString("rel=\"last\"")))
            .andExpect(header().string("Link", containsString("rel=\"first\"")))
            .andExpect(header().string("Link", not(containsString("rel=\"next\""))));
    }

    @Test
    void getAuditsRetainsPagingAndDateQueryParameters() throws Exception {
        for (int i = 0; i < 3; i++) {
            PersistentAuditEvent event = new PersistentAuditEvent();
            event.setAuditEventType(SAMPLE_TYPE + i);
            event.setPrincipal(SAMPLE_PRINCIPAL + i);
            event.setAuditEventDate(SAMPLE_TIMESTAMP.plusSeconds(i));
            event.setData(Map.of("index", Integer.toString(i)));
            auditEventRepository.save(event);
        }
        auditEventRepository.flush();

        restAuditMockMvc.perform(get("/management/audits?fromDate=2015-08-03&toDate=2015-08-05&page=0&size=1&sort=id,asc"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Total-Count", "3"))
            .andExpect(header().string("Link", containsString("fromDate=2015-08-03")))
            .andExpect(header().string("Link", containsString("toDate=2015-08-05")))
            .andExpect(header().string("Link", containsString("page=1")))
            .andExpect(header().string("Link", containsString("size=1")))
            .andExpect(header().string("Link", containsString("sort=id%2Casc")))
            .andExpect(header().string("Link", containsString("rel=\"next\"")))
            .andExpect(header().string("Link", containsString("rel=\"last\"")))
            .andExpect(header().string("Link", containsString("rel=\"first\"")));

        restAuditMockMvc.perform(get("/management/audits?fromDate=2015-08-03&toDate=2015-08-05&page=1&size=1&sort=id,asc"))
            .andExpect(status().isOk())
            .andExpect(header().string("Link", containsString("page=0")))
            .andExpect(header().string("Link", containsString("page=2")))
            .andExpect(header().string("Link", containsString("rel=\"prev\"")))
            .andExpect(header().string("Link", containsString("rel=\"next\"")));
    }

    @Test
    void getNonExistingAudit() throws Exception {
        // Get the audit
        restAuditMockMvc.perform(get("/management/audits/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @WithAnonymousUser
    void getAllAuditsAsAnonymousIsUnauthorized() throws Exception {
        restAuditMockMvc.perform(get("/management/audits"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = AuthoritiesConstants.USER)
    void getAllAuditsAsUserIsForbidden() throws Exception {
        restAuditMockMvc.perform(get("/management/audits"))
            .andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    void testPersistentAuditEventEquals() throws Exception {
        TestUtil.equalsVerifier(PersistentAuditEvent.class);
        PersistentAuditEvent auditEvent1 = new PersistentAuditEvent();
        auditEvent1.setId(1L);
        PersistentAuditEvent auditEvent2 = new PersistentAuditEvent();
        auditEvent2.setId(auditEvent1.getId());
        assertThat(auditEvent1).isEqualTo(auditEvent2);
        auditEvent2.setId(2L);
        assertThat(auditEvent1).isNotEqualTo(auditEvent2);
        auditEvent1.setId(null);
        assertThat(auditEvent1).isNotEqualTo(auditEvent2);
    }
}
