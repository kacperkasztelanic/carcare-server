package com.kasztelanic.carcare.service.dto;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

import java.time.LocalDate;
import java.util.Map;

@Value(staticConstructor = "of")
@EqualsAndHashCode(of = {"periodVehicle"})
@ToString(of = {"periodVehicle"}, includeFieldNames = false)
public class MileageResult {

    PeriodVehicle periodVehicle;
    Map<LocalDate, Integer> mileageByDate;
}
