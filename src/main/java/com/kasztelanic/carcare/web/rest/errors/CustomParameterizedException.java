package com.kasztelanic.carcare.web.rest.errors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

import java.util.HashMap;
import java.util.Map;

/**
 * Custom, parameterized exception, which can be translated on the client side.
 * For example:
 *
 * <pre>
 * throw new CustomParameterizedException(&quot;myCustomError&quot;, &quot;hello&quot;, &quot;world&quot;);
 * </pre>
 * <p>
 * Can be translated with:
 *
 * <pre>
 * "error.myCustomError" :  "The server says {{param0}} to {{param1}}"
 * </pre>
 */
public class CustomParameterizedException extends ErrorResponseException {

    private static final String PARAM = "param";

    private final String message;

    public CustomParameterizedException(String message, String... params) {
        this(message, toParamMap(params));
    }

    public CustomParameterizedException(String message, Map<String, Object> paramMap) {
        super(HttpStatus.BAD_REQUEST, problemDetail(message, paramMap), null);
        this.message = message;
    }

    /**
     * See {@link BadRequestAlertException#getMessage()}. Returns the caller-supplied message key
     * rather than the title, which is the constant {@code "Parameterized Exception"} here and so
     * would identify nothing in a log.
     */
    @Override
    public String getMessage() {
        return message;
    }

    public static Map<String, Object> toParamMap(String... params) {
        Map<String, Object> paramMap = new HashMap<>();
        if (params != null && params.length > 0) {
            for (int i = 0; i < params.length; i++) {
                paramMap.put(PARAM + i, params[i]);
            }
        }
        return paramMap;
    }

    private static ProblemDetail problemDetail(String message, Map<String, Object> paramMap) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setType(ErrorConstants.PARAMETERIZED_TYPE);
        problemDetail.setTitle("Parameterized Exception");
        problemDetail.setProperty("message", message);
        problemDetail.setProperty("params", paramMap);
        return problemDetail;
    }
}
