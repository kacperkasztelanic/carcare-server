package com.kasztelanic.carcare.service.dto;

import java.time.LocalDate;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

@Value(staticConstructor = "of")
@EqualsAndHashCode
@ToString(includeFieldNames = false)
public class VehicleEventDto {

    public static final VehicleEventDto ZERO = VehicleEventDto.of(0, LocalDate.MIN);

    Integer mileage;
    LocalDate date;
}
