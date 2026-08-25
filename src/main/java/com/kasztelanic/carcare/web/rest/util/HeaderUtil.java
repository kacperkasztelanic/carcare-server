package com.kasztelanic.carcare.web.rest.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for HTTP headers creation.
 * <p>
 * {@link #applicationName} is seeded from {@code spring.application.name} by
 * {@link com.kasztelanic.carcare.config.HeaderUtilInitializer}, since this static utility has no
 * Spring injection point of its own.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class HeaderUtil {

    private static volatile String applicationName = "carcare";

    /**
     * Namespace for the i18n message keys carried in alert headers. This is a <em>client
     * translation namespace</em>, not the application name: the client's per-language
     * {@code carcare.json} bundles are rooted at {@code carcareApp}, so this is deliberately
     * independent of {@code spring.application.name} and must not be folded into
     * {@link #applicationName}. Renaming it silently breaks every alert toast on the client.
     */
    private static final String TRANSLATION_KEY_NAMESPACE = "carcareApp";

    public static void setApplicationName(String name) {
        applicationName = name;
    }

    public static HttpHeaders createAlert(String message, String param) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-" + applicationName + "-alert", message);
        headers.add("X-" + applicationName + "-params", param);
        return headers;
    }

    public static HttpHeaders createAlert(String applicationName, String message, String param) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-" + applicationName + "-alert", message);
        try {
            headers.add("X-" + applicationName + "-params", URLEncoder.encode(param, StandardCharsets.UTF_8.toString()));
        } catch (UnsupportedEncodingException e) {
            // ignore
        }
        return headers;
    }

    public static HttpHeaders createFailureAlert(String applicationName, boolean enableTranslation, String entityName,
                                                  String errorKey, String defaultMessage) {
        log.error("Entity processing failed, {}", defaultMessage);
        String message = enableTranslation ? ("error." + errorKey) : defaultMessage;
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-" + applicationName + "-error", message);
        headers.add("X-" + applicationName + "-params", entityName);
        return headers;
    }

    public static HttpHeaders createEntityCreationAlert(String entityName, String param) {
        return createAlert(TRANSLATION_KEY_NAMESPACE + "." + entityName + ".created", param);
    }

    public static HttpHeaders createEntityUpdateAlert(String entityName, String param) {
        return createAlert(TRANSLATION_KEY_NAMESPACE + "." + entityName + ".updated", param);
    }

    public static HttpHeaders createEntityDeletionAlert(String entityName, String param) {
        return createAlert(TRANSLATION_KEY_NAMESPACE + "." + entityName + ".deleted", param);
    }

    public static HttpHeaders createFailureAlert(String entityName, String errorKey, String defaultMessage) {
        log.error("Entity processing failed, {}", defaultMessage);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-" + applicationName + "-error", "error." + errorKey);
        headers.add("X-" + applicationName + "-params", entityName);
        return headers;
    }
}
