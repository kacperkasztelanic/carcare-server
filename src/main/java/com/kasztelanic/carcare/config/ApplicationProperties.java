package com.kasztelanic.carcare.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.web.cors.CorsConfiguration;

/**
 * Properties specific to Carcare.
 * <p>
 * Properties are configured in the application.yml file.
 */
@ConfigurationProperties(prefix = "application", ignoreUnknownFields = true)
public class ApplicationProperties {

    @Getter
    private final DataDirectory dataDirectory = new DataDirectory();

    @Getter
    private final CorsConfiguration cors = new CorsConfiguration();

    @Getter
    private final Cache cache = new Cache();

    @Getter
    private final Security security = new Security();

    @Getter
    private final Mail mail = new Mail();

    @Getter
    private final AuditEvents auditEvents = new AuditEvents();

    @Getter
    private final Logging logging = new Logging();

    public static class DataDirectory {

        @Getter
        @Setter
        private String location;
    }

    public static class Cache {

        @Getter
        private final Ehcache ehcache = new Ehcache();

        public static class Ehcache {

            @Getter
            @Setter
            private int timeToLiveSeconds = 3600;

            @Getter
            @Setter
            private long maxEntries = 100;
        }
    }

    public static class Security {

        @Getter
        @Setter
        private String contentSecurityPolicy;

        @Getter
        private final Authentication authentication = new Authentication();

        public static class Authentication {

            @Getter
            private final Jwt jwt = new Jwt();

            public static class Jwt {

                @Getter
                @Setter
                private String secret;

                @Getter
                @Setter
                private String base64Secret;

                @Getter
                @Setter
                private long tokenValidityInSeconds;

                @Getter
                @Setter
                private long tokenValidityInSecondsForRememberMe;
            }
        }
    }

    public static class Mail {

        @Getter
        @Setter
        private String from;

        @Getter
        @Setter
        private String baseUrl;
    }

    public static class AuditEvents {

        @Getter
        @Setter
        private int retentionPeriod = 30;
    }

    public static class Logging {

        @Getter
        @Setter
        private boolean useJsonFormat;

        @Getter
        private final Logstash logstash = new Logstash();

        public static class Logstash {

            @Getter
            @Setter
            private boolean enabled;

            @Getter
            @Setter
            private String host;

            @Getter
            @Setter
            private int port;

            @Getter
            @Setter
            private int queueSize;
        }
    }
}
