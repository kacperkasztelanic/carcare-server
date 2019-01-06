package com.kasztelanic.carcare.service.dto;

import java.time.LocalDate;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@AllArgsConstructor(staticName = "of")
@EqualsAndHashCode(of = { "periodVehicle" })
@ToString(of = { "periodVehicle" }, includeFieldNames = false)
public class MileageResult {

    @Getter
    private final PeriodVehicle periodVehicle;
    @Getter
    private final Map<LocalDate, Integer> mileageByDate;
}
