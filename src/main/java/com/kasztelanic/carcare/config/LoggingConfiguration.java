package com.kasztelanic.carcare.config;

import ch.qos.logback.classic.LoggerContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kasztelanic.carcare.config.logging.LoggingUtils;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/*
 * Configures the console and Logstash log appenders from the app properties
 */
@Configuration
public class LoggingConfiguration {

    public LoggingConfiguration(
        @Value("${spring.application.name}") String appName,
        @Value("${server.port}") String serverPort,
        ApplicationProperties applicationProperties,
        ObjectMapper mapper
    ) throws JsonProcessingException {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

        Map<String, String> map = Map.of(
            "app_name", appName,
            "app_port", serverPort
        );
        String customFields = mapper.writeValueAsString(map);

        ApplicationProperties.Logging loggingProperties = applicationProperties.getLogging();
        ApplicationProperties.Logging.Logstash logstashProperties = loggingProperties.getLogstash();

        if (loggingProperties.isUseJsonFormat()) {
            LoggingUtils.addJsonConsoleAppender(context, customFields);
        }
        if (logstashProperties.isEnabled()) {
            LoggingUtils.addLogstashTcpSocketAppender(context, customFields, logstashProperties);
        }
        if (loggingProperties.isUseJsonFormat() || logstashProperties.isEnabled()) {
            LoggingUtils.addContextListener(context, customFields, loggingProperties);
        }
    }
}
