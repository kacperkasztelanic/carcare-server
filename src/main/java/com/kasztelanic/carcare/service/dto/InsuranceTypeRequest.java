package com.kasztelanic.carcare.service.dto;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@AllArgsConstructor(staticName = "of")
@EqualsAndHashCode(of = { "type" })
@ToString(of = { "type" }, includeFieldNames = false)
public class InsuranceTypeRequest {

    @Getter
    private final String type;
    @Getter
    private final String englishTranslation;
    @Getter
    private final String polishTranslation;
}