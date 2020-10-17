package com.kasztelanic.carcare.service.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.ToString;
import lombok.Value;

@Value(staticConstructor = "of")
@ToString(includeFieldNames = false)
public class CostRequest {

    List<Long> vehicleIds;
    LocalDate dateFrom;
    LocalDate dateTo;
}
