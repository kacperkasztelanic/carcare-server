package com.kasztelanic.carcare.config;

import org.springframework.web.servlet.i18n.CookieLocaleResolver;

import java.util.Locale;

/**
 * A {@link CookieLocaleResolver} that understands the Angular {@code $cookies} service's
 * convention of JSON-encoding cookie values, so a value like {@code %22en%22} (a
 * URL-encoded, double-quoted string) is read as plain {@code en}, and is written back in
 * the quoted form the client expects.
 * <p>
 * Both directions are handled through the hooks {@link CookieLocaleResolver} provides, so
 * the inherited cookie traversal keeps its invalid-cookie guard and its {@code /error}
 * dispatch handling.
 * <p>
 * Only the locale segment is quoted. Nothing in this application writes a time zone into
 * the cookie &mdash; {@code LocaleChangeInterceptor} supplies a locale-only context &mdash;
 * so the combined {@code "<locale> <zone>"} form never occurs here.
 */
public class QuotedCookieLocaleResolver extends CookieLocaleResolver {

    private static final String QUOTE = "%22";

    public QuotedCookieLocaleResolver(String cookieName) {
        super(cookieName);
    }

    @Override
    protected Locale parseLocaleValue(String localeValue) {
        return super.parseLocaleValue(localeValue.replace(QUOTE, "").replace('-', '_'));
    }

    @Override
    protected String toLocaleValue(Locale locale) {
        return QUOTE + super.toLocaleValue(locale) + QUOTE;
    }
}
