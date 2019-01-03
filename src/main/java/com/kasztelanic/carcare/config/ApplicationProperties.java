package com.kasztelanic.carcare.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Properties specific to Carcare.
 * <p>
 * Properties are configured in the application.yml file. See
 * {@link io.github.jhipster.config.JHipsterProperties} for a good example.
 */
@ConfigurationProperties(prefix = "application", ignoreUnknownFields = false)
public class ApplicationProperties {

    @Getter
    private final DataDirectory dataDirectory = new DataDirectory();

    public static class DataDirectory {

        @Getter
        @Setter
        private String location;
    }
}
