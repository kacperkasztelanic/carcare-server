package com.kasztelanic.carcare.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.i18n.TimeZoneAwareLocaleContext;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.util.WebUtils;

import java.util.Locale;
import java.util.TimeZone;

/**
 * A {@link CookieLocaleResolver} that understands the Angular {@code $cookies} service's
 * convention of JSON-encoding cookie values, so a value like {@code %22en%22} (a
 * URL-encoded, double-quoted string) is parsed the same as the plain {@code en} form.
 */
public class QuotedCookieLocaleResolver extends CookieLocaleResolver {

    private static final String QUOTE = "%22";

    private final String cookieName;

    public QuotedCookieLocaleResolver(String cookieName) {
        super(cookieName);
        this.cookieName = cookieName;
    }

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        parseQuotedCookieIfNecessary(request);
        return (Locale) request.getAttribute(LOCALE_REQUEST_ATTRIBUTE_NAME);
    }

    @Override
    public org.springframework.context.i18n.LocaleContext resolveLocaleContext(HttpServletRequest request) {
        parseQuotedCookieIfNecessary(request);
        return new TimeZoneAwareLocaleContext() {
            @Override
            public Locale getLocale() {
                return (Locale) request.getAttribute(LOCALE_REQUEST_ATTRIBUTE_NAME);
            }

            @Override
            public TimeZone getTimeZone() {
                return (TimeZone) request.getAttribute(TIME_ZONE_REQUEST_ATTRIBUTE_NAME);
            }
        };
    }

    private void parseQuotedCookieIfNecessary(HttpServletRequest request) {
        if (request.getAttribute(LOCALE_REQUEST_ATTRIBUTE_NAME) == null) {
            Cookie cookie = WebUtils.getCookie(request, cookieName);
            Locale locale = null;
            TimeZone timeZone = null;
            if (cookie != null) {
                String value = StringUtils.replace(cookie.getValue(), QUOTE, "");
                String localePart = value;
                String timeZonePart = null;
                int spaceIndex = localePart.indexOf(' ');
                if (spaceIndex != -1) {
                    localePart = value.substring(0, spaceIndex);
                    timeZonePart = value.substring(spaceIndex + 1);
                }
                locale = "-".equals(localePart) ? null : StringUtils.parseLocaleString(localePart.replace('-', '_'));
                if (timeZonePart != null) {
                    timeZone = StringUtils.parseTimeZoneString(timeZonePart);
                }
            }
            request.setAttribute(LOCALE_REQUEST_ATTRIBUTE_NAME,
                locale != null ? locale : determineDefaultLocale(request));
            request.setAttribute(TIME_ZONE_REQUEST_ATTRIBUTE_NAME,
                timeZone != null ? timeZone : determineDefaultTimeZone(request));
        }
    }
}
