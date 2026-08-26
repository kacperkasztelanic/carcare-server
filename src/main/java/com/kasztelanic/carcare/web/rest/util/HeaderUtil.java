package com.kasztelanic.carcare.web.rest.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for HTTP alert header creation.
 * <p>
 * {@link #HEADER_NAME_PREFIX} is the fixed client-facing prefix for the alert header names.
 * {@link #TRANSLATION_KEY_NAMESPACE} is the independent i18n root for alert values. Both contracts
 * currently use {@code carcareApp}, but neither is derived from {@code spring.application.name}.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class HeaderUtil {

    private static final String HEADER_NAME_PREFIX = "carcareApp";

    /**
     * Namespace for the i18n message keys carried in alert header values. This is a <em>client
     * translation namespace</em>, distinct from {@link #HEADER_NAME_PREFIX}; the client's
     * per-language {@code carcare.json} bundles are rooted at {@code carcareApp}.
     */
    private static final String TRANSLATION_KEY_NAMESPACE = "carcareApp";

    public static HttpHeaders createAlert(String message, String param) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-" + HEADER_NAME_PREFIX + "-alert", message);
        headers.add("X-" + HEADER_NAME_PREFIX + "-params", param);
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
        headers.add("X-" + HEADER_NAME_PREFIX + "-error", "error." + errorKey);
        headers.add("X-" + HEADER_NAME_PREFIX + "-params", entityName);
        return headers;
    }
}
