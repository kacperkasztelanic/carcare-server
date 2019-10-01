package com.kasztelanic.carcare.service.dto;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@AllArgsConstructor(staticName = "of")
@EqualsAndHashCode(of = { "name" })
@ToString(of = { "name" }, includeFieldNames = false)
public class Report {

    @Getter
    private final String name;
    @Getter
    private final byte[] bytes;
}
