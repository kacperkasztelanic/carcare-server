package com.kasztelanic.carcare.service.dto;

import lombok.ToString;
import lombok.Value;

import java.time.LocalDate;

@Value(staticConstructor = "of")
@ToString(includeFieldNames = false)
public class PeriodVehicle {

    Long vehicleId;
    LocalDate dateFrom;
    LocalDate dateTo;
}
