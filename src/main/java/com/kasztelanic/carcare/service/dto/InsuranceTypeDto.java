package com.kasztelanic.carcare.service.dto;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

@Value(staticConstructor = "of")
@EqualsAndHashCode(of = {"type"})
@ToString(of = {"type"}, includeFieldNames = false)
public class InsuranceTypeDto {

    String type;
    String translation;
}
