package com.kasztelanic.carcare.config.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggerContextListener;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.spi.ContextAwareBase;
import com.kasztelanic.carcare.config.ApplicationProperties;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import net.logstash.logback.appender.LogstashTcpSocketAppender;
import net.logstash.logback.composite.ContextJsonProvider;
import net.logstash.logback.composite.GlobalCustomFieldsJsonProvider;
import net.logstash.logback.composite.loggingevent.ArgumentsJsonProvider;
import net.logstash.logback.composite.loggingevent.LogLevelJsonProvider;
import net.logstash.logback.composite.loggingevent.LoggerNameJsonProvider;
import net.logstash.logback.composite.loggingevent.LoggingEventFormattedTimestampJsonProvider;
import net.logstash.logback.composite.loggingevent.LoggingEventJsonProviders;
import net.logstash.logback.composite.loggingevent.LoggingEventPatternJsonProvider;
import net.logstash.logback.composite.loggingevent.LoggingEventThreadNameJsonProvider;
import net.logstash.logback.composite.loggingevent.MdcJsonProvider;
import net.logstash.logback.composite.loggingevent.MessageJsonProvider;
import net.logstash.logback.composite.loggingevent.StackTraceJsonProvider;
import net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder;
import net.logstash.logback.encoder.LogstashEncoder;
import net.logstash.logback.stacktrace.ShortenedThrowableConverter;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

/**
 * Configures the console and Logstash log appenders from the app properties.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LoggingUtils {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(LoggingUtils.class);

    private static final String CONSOLE_APPENDER_NAME = "CONSOLE";
    private static final String ASYNC_LOGSTASH_APPENDER_NAME = "ASYNC_LOGSTASH";

    public static void addJsonConsoleAppender(LoggerContext context, String customFields) {
        log.info("Initializing Console loggingProperties");
        ConsoleAppender<ch.qos.logback.classic.spi.ILoggingEvent> consoleAppender = new ConsoleAppender<>();
        consoleAppender.setContext(context);
        consoleAppender.setEncoder(compositeJsonEncoder(context, customFields));
        consoleAppender.setName(CONSOLE_APPENDER_NAME);
        consoleAppender.start();
        context.getLogger(Logger.ROOT_LOGGER_NAME).detachAppender(CONSOLE_APPENDER_NAME);
        context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(consoleAppender);
    }

    public static void addLogstashTcpSocketAppender(LoggerContext context, String customFields,
                                                      ApplicationProperties.Logging.Logstash logstashProperties) {
        log.info("Initializing Logstash loggingProperties");
        LogstashTcpSocketAppender logstashAppender = new LogstashTcpSocketAppender();
        logstashAppender.addDestinations(new InetSocketAddress(logstashProperties.getHost(), logstashProperties.getPort()));
        logstashAppender.setContext(context);
        logstashAppender.setEncoder(logstashEncoder(customFields));
        logstashAppender.setName(ASYNC_LOGSTASH_APPENDER_NAME);
        logstashAppender.setRingBufferSize(logstashProperties.getQueueSize());
        logstashAppender.start();
        context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(logstashAppender);
    }

    public static void addContextListener(LoggerContext context, String customFields,
                                           ApplicationProperties.Logging loggingProperties) {
        LogbackLoggerContextListener listener = new LogbackLoggerContextListener(loggingProperties, customFields);
        listener.setContext(context);
        context.addListener(listener);
    }

    private static LoggingEventCompositeJsonEncoder compositeJsonEncoder(LoggerContext context, String customFields) {
        LoggingEventCompositeJsonEncoder encoder = new LoggingEventCompositeJsonEncoder();
        encoder.setContext(context);
        encoder.setProviders(jsonProviders(context, customFields));
        encoder.start();
        return encoder;
    }

    private static LogstashEncoder logstashEncoder(String customFields) {
        LogstashEncoder encoder = new LogstashEncoder();
        encoder.setThrowableConverter(throwableConverter());
        encoder.setCustomFields(customFields);
        return encoder;
    }

    private static LoggingEventJsonProviders jsonProviders(LoggerContext context, String customFields) {
        LoggingEventJsonProviders providers = new LoggingEventJsonProviders();
        providers.addArguments(new ArgumentsJsonProvider());
        providers.addContext(new ContextJsonProvider<>());
        providers.addGlobalCustomFields(customFieldsJsonProvider(customFields));
        providers.addLogLevel(new LogLevelJsonProvider());
        providers.addLoggerName(loggerNameJsonProvider());
        providers.addMdc(new MdcJsonProvider());
        providers.addMessage(new MessageJsonProvider());
        providers.addPattern(new LoggingEventPatternJsonProvider());
        providers.addStackTrace(stackTraceJsonProvider());
        providers.addThreadName(new LoggingEventThreadNameJsonProvider());
        providers.addTimestamp(timestampJsonProvider());
        providers.setContext(context);
        return providers;
    }

    private static GlobalCustomFieldsJsonProvider<ch.qos.logback.classic.spi.ILoggingEvent> customFieldsJsonProvider(String customFields) {
        GlobalCustomFieldsJsonProvider<ch.qos.logback.classic.spi.ILoggingEvent> provider = new GlobalCustomFieldsJsonProvider<>();
        provider.setCustomFields(customFields);
        return provider;
    }

    private static LoggerNameJsonProvider loggerNameJsonProvider() {
        LoggerNameJsonProvider provider = new LoggerNameJsonProvider();
        provider.setShortenedLoggerNameLength(20);
        return provider;
    }

    private static StackTraceJsonProvider stackTraceJsonProvider() {
        StackTraceJsonProvider provider = new StackTraceJsonProvider();
        provider.setThrowableConverter(throwableConverter());
        return provider;
    }

    private static ShortenedThrowableConverter throwableConverter() {
        ShortenedThrowableConverter converter = new ShortenedThrowableConverter();
        converter.setRootCauseFirst(true);
        return converter;
    }

    private static LoggingEventFormattedTimestampJsonProvider timestampJsonProvider() {
        LoggingEventFormattedTimestampJsonProvider provider = new LoggingEventFormattedTimestampJsonProvider();
        provider.setTimeZone("UTC");
        provider.setFieldName("timestamp");
        return provider;
    }

    @RequiredArgsConstructor
    private static class LogbackLoggerContextListener extends ContextAwareBase implements LoggerContextListener {

        private final ApplicationProperties.Logging loggingProperties;
        private final String customFields;

        @Override
        public boolean isResetResistant() {
            return true;
        }

        @Override
        public void onStart(LoggerContext context) {
            configure(context);
        }

        @Override
        public void onReset(LoggerContext context) {
            configure(context);
        }

        @Override
        public void onStop(LoggerContext context) {
            // nothing to do
        }

        @Override
        public void onLevelChange(Logger logger, Level level) {
            // nothing to do
        }

        private void configure(LoggerContext context) {
            if (loggingProperties.isUseJsonFormat()) {
                addJsonConsoleAppender(context, customFields);
            }
            if (loggingProperties.getLogstash().isEnabled()) {
                addLogstashTcpSocketAppender(context, customFields, loggingProperties.getLogstash());
            }
        }
    }
}
