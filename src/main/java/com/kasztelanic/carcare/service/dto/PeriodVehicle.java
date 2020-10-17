package com.kasztelanic.carcare.service.dto;

import java.time.LocalDate;

import lombok.ToString;
import lombok.Value;

@Value(staticConstructor = "of")
@ToString(includeFieldNames = false)
public class PeriodVehicle {

    Long vehicleId;
    LocalDate dateFrom;
    LocalDate dateTo;
}
