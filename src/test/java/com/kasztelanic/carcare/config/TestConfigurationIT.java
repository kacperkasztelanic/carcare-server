package com.kasztelanic.carcare.config;

import com.kasztelanic.carcare.CarcareApp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the {@code test} profile layering (see {@code src/test/resources/config/application.properties}
 * and {@code application-test.yml}) against the shadowing regression it replaces: the test resource
 * used to occupy the same classpath location as {@code src/main/resources/config/application.yml} and
 * therefore replaced it outright instead of layering on top of it.
 */
@SpringBootTest(classes = CarcareApp.class)
class TestConfigurationIT {

    @Autowired
    private Environment env;

    @Autowired
    private ApplicationProperties applicationProperties;

    @Test
    void testProfileIsActiveAndDevIsNot() {
        assertThat(env.getActiveProfiles()).contains("test");
        assertThat(env.getActiveProfiles()).doesNotContain("dev");
    }

    @Test
    void datasourceIsH2() {
        assertThat(env.getProperty("spring.datasource.url")).contains("jdbc:h2:mem:");
        assertThat(env.getProperty("spring.jpa.database")).isEqualTo("H2");
    }

    @Test
    void liquibaseUsesSharedMasterChangelogUnderTestContext() {
        assertThat(env.getProperty("spring.liquibase.change-log"))
            .isEqualTo("classpath:config/liquibase/master.xml");
        assertThat(env.getProperty("spring.liquibase.contexts")).isEqualTo("test");
    }

    @Test
    void managementStaysUnderManagementBasePathWithMailHealthDisabled() {
        assertThat(env.getProperty("management.endpoints.web.base-path")).isEqualTo("/management");
        assertThat(env.getProperty("management.health.mail.enabled", Boolean.class)).isFalse();
    }

    @Test
    void smtpIsNeutralizedToLocalhostWithoutAuthOrStarttlsOrInheritedGmailTls() {
        assertThat(env.getProperty("spring.mail.host")).isEqualTo("localhost");
        assertThat(env.getProperty("spring.mail.port", Integer.class)).isEqualTo(25);
        assertThat(env.getProperty("spring.mail.tls", Boolean.class)).isFalse();
        assertThat(env.getProperty("spring.mail.properties.mail.smtp.auth", Boolean.class)).isFalse();
        assertThat(env.getProperty("spring.mail.properties.mail.smtp.starttls.enable", Boolean.class)).isFalse();
        assertThat(env.getProperty("spring.mail.properties.mail.smtp.ssl.trust")).isNullOrEmpty();
    }

    @Test
    void contentSecurityPolicyIsInheritedAndNonBlank() {
        assertThat(applicationProperties.getSecurity().getContentSecurityPolicy()).isNotBlank();
    }
}
