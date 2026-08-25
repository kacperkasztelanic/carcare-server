package com.kasztelanic.carcare.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Test-only H2 compatibility fixture. Liquibase seeds {@code jhi_user} with explicit ids 1-4
 * (see {@code config/liquibase/data/user.csv}). H2 1.4.200 auto-advanced an identity column's
 * counter past a manually inserted value; H2 2.1.214 (the version Spring Boot 3 manages) does
 * not, so the first test that persists a new {@code User} without an explicit id collides with
 * a seeded row. Restarting the identity counter once, after Liquibase has finished seeding,
 * fixes this without touching Liquibase, production entities, or the MariaDB-backed schema.
 */
@Component
@Profile("test")
@RequiredArgsConstructor
public class TestUserIdentitySequenceFixup implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        Long nextId = jdbcTemplate.queryForObject("SELECT MAX(id) + 1 FROM jhi_user", Long.class);
        if (nextId != null) {
            jdbcTemplate.execute("ALTER TABLE jhi_user ALTER COLUMN id RESTART WITH " + nextId);
        }
    }
}
