package com.kasztelanic.carcare.service.dto;

import lombok.ToString;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;

@Value(staticConstructor = "of")
@ToString(includeFieldNames = false)
public class CostRequest {

    List<Long> vehicleIds;
    LocalDate dateFrom;
    LocalDate dateTo;
}
