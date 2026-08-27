package com.kasztelanic.carcare.service.dto;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.hibernate.validator.constraints.Length;

import jakarta.validation.constraints.NotNull;

@Getter
@Builder
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = {"id"})
@ToString(of = {"make", "model", "licensePlate"}, includeFieldNames = false)
public class VehicleDto {

    Long id;

    @NotNull
    @Length(min = 1, max = 20)
    String make;

    @NotNull
    @Length(min = 1, max = 20)
    String model;

    @NotNull
    @Length(min = 1, max = 20)
    String licensePlate;

    FuelTypeDto fuelType;
    VehicleDetailsDto vehicleDetails;
}
