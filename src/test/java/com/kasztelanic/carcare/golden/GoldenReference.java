package com.kasztelanic.carcare.golden;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads a committed golden response and compares it with a live response.
 *
 * <p>JSON numbers are normalised to the fixed-precision strings used by the capture.  The
 * normalisation also replaces every statistics {@code vehicleId} with its symbolic fixture
 * handle, using the map returned by {@code SessionFixtures.seedGoldenDataset()}.  Workbook bodies
 * are reduced by {@link WorkbookValues}; status and controller-owned headers are compared for both
 * response kinds.</p>
 */
public final class GoldenReference {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final List<String> CAPTURED_HEADERS = List.of(
        "Content-Type", "Content-Disposition", "Cache-Control", "X-Total-Count");
    /** Namespace for handles that represent ids serialized in vehicleId fields and raw bodies. */
    private static final String VEHICLE_HANDLE_NAMESPACE = "vehicle:";

    private final String resourceName;
    private final JsonNode expected;

    private GoldenReference(String resourceName, JsonNode expected) {
        this.resourceName = resourceName;
        this.expected = expected;
    }

    /** Loads a response from the test runtime classpath. */
    public static GoldenReference load(String resourceName) {
        String classpathName = resourceName.startsWith("/") ? resourceName.substring(1) : resourceName;
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        InputStream stream = loader.getResourceAsStream(classpathName);
        if (stream == null) {
            throw new IllegalArgumentException("Golden reference not found on the classpath: " + resourceName);
        }
        try (InputStream input = stream) {
            return new GoldenReference(resourceName, MAPPER.readTree(input));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not read golden reference " + resourceName, exception);
        }
    }

    public String resourceName() {
        return resourceName;
    }

    public int status() {
        return expected.path("status").asInt();
    }

    /** Compares a JSON response represented by a MockMvc response. */
    public Comparison compareJson(MockHttpServletResponse response, Map<String, Long> handleToId) {
        return compareJson(response.getStatus(), headers(response), response.getContentAsByteArray(), handleToId);
    }

    /** Compares a JSON response returned by MockMvc. */
    public Comparison compareJson(MvcResult result, Map<String, Long> handleToId) {
        return compareJson(result.getResponse(), handleToId);
    }

    /** Compares an XLSX response represented by a MockMvc response. */
    public Comparison compareWorkbook(MockHttpServletResponse response, Map<String, Long> handleToId) {
        return compareWorkbook(response.getStatus(), headers(response), response.getContentAsByteArray(), handleToId);
    }

    /** Compares an XLSX response returned by MockMvc. */
    public Comparison compareWorkbook(MvcResult result, Map<String, Long> handleToId) {
        return compareWorkbook(result.getResponse(), handleToId);
    }

    public void assertJsonMatches(MockHttpServletResponse response, Map<String, Long> handleToId) {
        compareJson(response, handleToId).assertMatches();
    }

    public void assertJsonMatches(MvcResult result, Map<String, Long> handleToId) {
        compareJson(result, handleToId).assertMatches();
    }

    public void assertWorkbookMatches(MockHttpServletResponse response, Map<String, Long> handleToId) {
        compareWorkbook(response, handleToId).assertMatches();
    }

    public void assertWorkbookMatches(MvcResult result, Map<String, Long> handleToId) {
        compareWorkbook(result, handleToId).assertMatches();
    }

    /** Compares a JSON response supplied as status, headers, and UTF-8 bytes. */
    public Comparison compareJson(int status, Map<String, String> headers, byte[] body,
                                  Map<String, Long> handleToId) {
        Map<String, Long> safeHandles = validateHandleMap(handleToId);
        JsonNode expectedBody = expected.path("body");
        JsonNode actualBody;
        String rawBody = new String(body, StandardCharsets.UTF_8);
        if (expectedBody.isTextual()) {
            actualBody = TextNode.valueOf(normalizeRawHandles(rawBody, safeHandles));
        } else if (rawBody.isBlank()) {
            actualBody = NullNode.getInstance();
        } else {
            try {
                actualBody = normalizeJson(MAPPER.readTree(rawBody), null, safeHandles);
            } catch (IllegalArgumentException exception) {
                return Comparison.failure(resourceName + " could not resolve a live identity: " + exception.getMessage());
            } catch (JsonProcessingException exception) {
                return Comparison.failure(resourceName + " live body is not valid JSON: " + exception.getOriginalMessage());
            }
        }
        return compareEnvelope(status, headers, actualBody);
    }

    /** Compares an XLSX response supplied as status, headers, and raw bytes. */
    public Comparison compareWorkbook(int status, Map<String, String> headers, byte[] body,
                                      Map<String, Long> handleToId) {
        validateHandleMap(handleToId);
        JsonNode expectedBody = expected.path("body");
        JsonNode actualBody;
        if (body.length == 0 && expectedBody.isNull()) {
            actualBody = NullNode.getInstance();
        } else {
            try {
                actualBody = WorkbookValues.extract(body).asJson();
            } catch (IOException | RuntimeException exception) {
                return Comparison.failure(resourceName + " live body is not a readable XLSX: " + exception.getMessage());
            }
        }
        return compareEnvelope(status, headers, actualBody);
    }

    public void assertJsonMatches(int status, Map<String, String> headers, byte[] body,
                                  Map<String, Long> handleToId) {
        compareJson(status, headers, body, handleToId).assertMatches();
    }

    public void assertWorkbookMatches(int status, Map<String, String> headers, byte[] body,
                                      Map<String, Long> handleToId) {
        compareWorkbook(status, headers, body, handleToId).assertMatches();
    }

    /** A non-throwing result useful for tests that want to inspect the first mismatch. */
    public record Comparison(boolean matches, String message) {
        private static Comparison failure(String message) {
            return new Comparison(false, message);
        }

        public void assertMatches() {
            if (!matches) {
                throw new AssertionError(message);
            }
        }
    }

    private Comparison compareEnvelope(int status, Map<String, String> headers, JsonNode actualBody) {
        ObjectNode actual = JsonNodeFactory.instance.objectNode();
        actual.put("status", status);
        actual.set("headers", MAPPER.valueToTree(selectHeaders(headers)));
        actual.set("body", actualBody);
        String difference = firstDifference(expected, actual, "$");
        return difference == null
            ? new Comparison(true, resourceName + " matches")
            : Comparison.failure(resourceName + " mismatch at " + difference);
    }

    private static Map<String, String> selectHeaders(Map<String, String> headers) {
        Map<String, String> selected = new LinkedHashMap<>();
        for (String expectedName : CAPTURED_HEADERS) {
            headers.entrySet().stream()
                .filter(entry -> expectedName.equalsIgnoreCase(entry.getKey()))
                .findFirst()
                .ifPresent(entry -> selected.put(expectedName, entry.getValue()));
        }
        return selected;
    }

    private static Map<String, String> headers(MockHttpServletResponse response) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (String name : response.getHeaderNames()) {
            String value = response.getHeader(name);
            if (value != null) {
                headers.put(name, value);
            }
        }
        return headers;
    }

    private static Map<String, Long> validateHandleMap(Map<String, Long> handleToId) {
        Objects.requireNonNull(handleToId, "handleToId");
        Map<String, Long> copy = new LinkedHashMap<>();
        Map<Long, String> seenIds = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : handleToId.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException("Golden handle map cannot contain null keys or values");
            }
            if (!entry.getKey().startsWith(VEHICLE_HANDLE_NAMESPACE)) {
                continue;
            }
            String previous = seenIds.put(entry.getValue(), entry.getKey());
            if (previous != null && !previous.equals(entry.getKey())) {
                throw new IllegalArgumentException("Golden handle map resolves id " + entry.getValue()
                    + " to both " + previous + " and " + entry.getKey());
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return copy;
    }

    private static JsonNode normalizeJson(JsonNode node, String fieldName, Map<String, Long> handleToId) {
        Map<Long, String> idToHandle = reverse(handleToId);
        return normalizeJsonById(node, fieldName, idToHandle);
    }

    private static JsonNode normalizeJsonById(JsonNode node, String fieldName, Map<Long, String> idToHandle) {
        if (node == null || node.isNull()) {
            return NullNode.getInstance();
        }
        if (node.isObject()) {
            ObjectNode reduced = JsonNodeFactory.instance.objectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                reduced.set(field.getKey(), normalizeJsonById(field.getValue(), field.getKey(), idToHandle));
            }
            return reduced;
        }
        if (node.isArray()) {
            ArrayNode reduced = JsonNodeFactory.instance.arrayNode();
            for (JsonNode value : node) {
                reduced.add(normalizeJsonById(value, null, idToHandle));
            }
            return reduced;
        }
        if ("vehicleId".equals(fieldName)) {
            String id = node.asText();
            Long parsedId = parseId(id);
            String handle = idToHandle.get(parsedId);
            if (handle == null && parsedId != null) {
                throw new IllegalArgumentException("Undocumented vehicle id " + id);
            }
            if (handle != null) {
                return TextNode.valueOf(handle);
            }
            return node;
        }
        if (node.isNumber()) {
            return TextNode.valueOf(decimal(node.decimalValue(), isMoney(fieldName) ? 2 : 6));
        }
        return node;
    }

    private static Long parseId(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Map<Long, String> reverse(Map<String, Long> handleToId) {
        Map<Long, String> reverse = new LinkedHashMap<>();
        handleToId.forEach((handle, id) -> reverse.put(id, handle));
        return reverse;
    }

    private static boolean isMoney(String fieldName) {
        return fieldName != null && (fieldName.endsWith("Costs") || "sum".equals(fieldName));
    }

    private static String decimal(java.math.BigDecimal value, int scale) {
        return value.setScale(scale, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * Rewrites ids back to handles in a body that is not parseable JSON. The lookarounds stop a
     * match inside a longer digit run ({@code 26} within {@code 2026}), but an id that is itself
     * delimited by non-digits is still replaced — {@code 31} in {@code "2026-03-31"} and
     * {@code 500} in {@code "error.http.500"} both match. No golden consumes this path today;
     * anchor the replacement to the {@code "vehicleId"} field before one does. Pinned by
     * {@code GoldenReferenceTest.rawHandleReplacementDoesNotGuardAnIdDelimitedByNonDigits}.
     */
    private static String normalizeRawHandles(String rawBody, Map<String, Long> handleToId) {
        String normalized = rawBody;
        List<Map.Entry<String, Long>> entries = new ArrayList<>(handleToId.entrySet());
        entries.sort((left, right) -> Long.compare(right.getValue(), left.getValue()));
        for (Map.Entry<String, Long> entry : entries) {
            String id = Pattern.quote(Long.toString(entry.getValue()));
            normalized = normalized.replaceAll("(?<!\\d)" + id + "(?!\\d)", Matcher.quoteReplacement(entry.getKey()));
        }
        return normalized;
    }

    private static String firstDifference(JsonNode expected, JsonNode actual, String path) {
        if (expected == null || expected.isNull()) {
            return actual == null || actual.isNull() ? null : path + ": expected null but was " + describe(actual);
        }
        if (actual == null || actual.isNull()) {
            return path + ": expected " + describe(expected) + " but was null";
        }
        if (expected.isObject()) {
            if (!actual.isObject()) {
                return path + ": expected object but was " + describe(actual);
            }
            Iterator<Map.Entry<String, JsonNode>> fields = expected.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String childPath = path + "." + field.getKey();
                if (!actual.has(field.getKey())) {
                    return childPath + ": expected " + describe(field.getValue()) + " but was missing";
                }
                String difference = firstDifference(field.getValue(), actual.get(field.getKey()), childPath);
                if (difference != null) {
                    return difference;
                }
            }
            Iterator<String> actualFields = actual.fieldNames();
            while (actualFields.hasNext()) {
                String field = actualFields.next();
                if (!expected.has(field)) {
                    return path + "." + field + ": unexpected " + describe(actual.get(field));
                }
            }
            return null;
        }
        if (expected.isArray()) {
            if (!actual.isArray()) {
                return path + ": expected array but was " + describe(actual);
            }
            if (expected.size() != actual.size()) {
                return path + ": expected " + expected.size() + " entries but was " + actual.size();
            }
            for (int index = 0; index < expected.size(); index++) {
                String difference = firstDifference(expected.get(index), actual.get(index), path + "[" + index + "]");
                if (difference != null) {
                    return difference;
                }
            }
            return null;
        }
        return expected.equals(actual)
            ? null
            : path + ": expected " + describe(expected) + " but was " + describe(actual);
    }

    private static String describe(JsonNode node) {
        if (node == null || node.isNull()) {
            return "null";
        }
        return node.isTextual() ? '"' + node.textValue() + '"' : node.toString();
    }
}
