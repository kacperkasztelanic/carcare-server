package com.kasztelanic.carcare.web.rest;

import com.kasztelanic.carcare.domain.User;
import com.kasztelanic.carcare.domain.Vehicle;
import com.kasztelanic.carcare.repository.InspectionRepository;
import com.kasztelanic.carcare.repository.InsuranceRepository;
import com.kasztelanic.carcare.repository.RoutineServiceRepository;
import com.kasztelanic.carcare.repository.UserRepository;
import com.kasztelanic.carcare.repository.VehicleRepository;
import com.kasztelanic.carcare.security.AuthoritiesConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Commit-path truth for the tombstone disposition (P2): the {@code owner_id} FK succeeds, owned
 * vehicles are reassigned to {@code anonymoususer}, previously-active vehicles are archived, and the
 * archived-on-reassign vehicles are excluded from reminder selection. None of this is observable
 * under the class-level {@code @Transactional} because a rollback hides commit-time FK behaviour.
 */
@WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
class UserDeletionDispositionIT extends AbstractSessionIT {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private VehicleRepository vehicleRepository;
    @Autowired
    private InsuranceRepository insuranceRepository;
    @Autowired
    private InspectionRepository inspectionRepository;
    @Autowired
    private RoutineServiceRepository routineServiceRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void deletingAVehicleOwnerTombstonesAndArchivesOwnedVehicles() throws Exception {
        String login = committedUser();
        Vehicle active = sessionFixtures.vehicleWithEventsFor(login);
        Vehicle archived = sessionFixtures.archive(sessionFixtures.vehicleWithEventsFor(login),
            Instant.parse("2024-06-01T00:00:00Z"));
        try {
            mockMvc.perform(delete("/api/users/{login}", login))
                .andExpect(status().isNoContent())
                .andExpect(header().string("X-carcare-alert", "userManagement.deleted"))
                .andExpect(header().string("X-carcare-params", login));

            assertThat(userRepository.findOneByLogin(login)).isEmpty();

            Vehicle reloadedActive = vehicleRepository.findById(active.getId()).orElseThrow();
            Vehicle reloadedArchived = vehicleRepository.findById(archived.getId()).orElseThrow();
            assertThat(reloadedActive.getOwner().getLogin()).isEqualTo("anonymoususer");
            assertThat(reloadedArchived.getOwner().getLogin()).isEqualTo("anonymoususer");
            assertThat(reloadedActive.getArchivedAt()).isNotNull();
            assertThat(reloadedArchived.getArchivedAt())
                .isEqualTo(Instant.parse("2024-06-01T00:00:00Z"));

            List<Long> tombstoned = List.of(active.getId(), archived.getId());
            // Fixture event dates: inspection validThru 2025-04-10, insurance validThru 2025-05-10,
            // routine-service nextByDate 2025-03-10 (see SessionFixtures).
            assertThat(inspectionRepository.findByValidThruIn(List.of(LocalDate.of(2025, 4, 10)))
                .stream().map(i -> i.getVehicle().getId())).doesNotContainAnyElementsOf(tombstoned);
            assertThat(insuranceRepository.findByValidThruIn(List.of(LocalDate.of(2025, 5, 10)))
                .stream().map(i -> i.getVehicle().getId())).doesNotContainAnyElementsOf(tombstoned);
            assertThat(routineServiceRepository.findByNextByDateIn(List.of(LocalDate.of(2025, 3, 10)))
                .stream().map(r -> r.getVehicle().getId())).doesNotContainAnyElementsOf(tombstoned);
        } finally {
            purgeRows(active.getId(), archived.getId());
            deleteUserIfPresent(login);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void deletingAUserWithNoVehiclesStillReturns204() throws Exception {
        String login = committedUser();
        try {
            mockMvc.perform(delete("/api/users/{login}", login))
                .andExpect(status().isNoContent())
                .andExpect(header().string("X-carcare-alert", "userManagement.deleted"));
            assertThat(userRepository.findOneByLogin(login)).isEmpty();
        } finally {
            deleteUserIfPresent(login);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void deletingANonexistentLoginReturns204() throws Exception {
        mockMvc.perform(delete("/api/users/{login}", "no-such-user-" + SEQ.incrementAndGet()))
            .andExpect(status().isNoContent())
            .andExpect(header().string("X-carcare-alert", "userManagement.deleted"));
    }

    private String committedUser() {
        String login = "disposition-" + SEQ.incrementAndGet();
        User user = new User();
        user.setLogin(login);
        user.setPassword(passwordEncoder.encode("disposition-secret"));
        user.setEmail(login + "@example.com");
        user.setFirstName("Disposition");
        user.setLastName("Test");
        user.setActivated(true);
        user.setLangKey("en");
        user.setCreatedBy("system");
        return userRepository.saveAndFlush(user).getLogin();
    }

    // The test datasource runs with hikari auto-commit=false, so raw JDBC statements outside a
    // Spring transaction are never committed; wrap every cleanup batch in a TransactionTemplate.
    private void inTransaction(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
    }

    private void purgeRows(Long... vehicleIds) {
        sessionFixtures.purgeRowsFor(List.of(vehicleIds));
    }

    private void deleteUserIfPresent(String login) {
        inTransaction(() -> {
            jdbcTemplate.update(
                "delete from jhi_user_authority where user_id in (select id from jhi_user where login = ?)", login);
            jdbcTemplate.update("delete from jhi_user where login = ?", login);
        });
    }
}
