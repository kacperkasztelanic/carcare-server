package com.kasztelanic.carcare.service.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Builder
@EqualsAndHashCode
@ToString(includeFieldNames = false)
public class CostReportRequest {

    @Getter
    private final List<Long> vehicleIds;
    @Getter
    private final LocalDate dateFrom;
    @Getter
    private final LocalDate dateTo;
}
