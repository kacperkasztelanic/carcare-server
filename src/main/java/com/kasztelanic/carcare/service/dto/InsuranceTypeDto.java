package com.kasztelanic.carcare.service.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

@Value(staticConstructor = "of")
@EqualsAndHashCode(of = {"type"})
@ToString(of = {"type"}, includeFieldNames = false)
public class InsuranceTypeDto {

    String type;
    String translation;

    /**
     * The client posts lookup types as objects but sends only the type string when editing.
     * Keep both request shapes readable while preserving the object form in every response.
     */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static InsuranceTypeDto fromJson(JsonNode value) {
        if (value.isTextual()) {
            return of(value.asText(), null);
        }
        return of(textValue(value, "type"), textValue(value, "translation"));
    }

    private static String textValue(JsonNode value, String fieldName) {
        JsonNode field = value.get(fieldName);
        return field != null && !field.isNull() ? field.asText() : null;
    }
}
