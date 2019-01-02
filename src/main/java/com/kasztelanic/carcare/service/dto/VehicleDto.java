package com.kasztelanic.carcare.service.dto;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Builder
@EqualsAndHashCode(of = { "id" })
@ToString(of = { "make", "model", "licensePlate" }, includeFieldNames = false)
public class VehicleDto {

    @Getter
    private final Long id;
    @Getter
    private final String make;
    @Getter
    private final String model;
    @Getter
    private final String licensePlate;
    @Getter
    private final String fuelType;
    @Getter
    private final VehicleDetailsDto vehicleDetails;
}
