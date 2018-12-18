package com.kasztelanic.carcare.service.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@AllArgsConstructor(staticName = "of")
@EqualsAndHashCode
@ToString(includeFieldNames = false)
public class VehicleEventDto {

    @Getter
    private final Integer mileage;
    @Getter
    private final LocalDate date;
}
