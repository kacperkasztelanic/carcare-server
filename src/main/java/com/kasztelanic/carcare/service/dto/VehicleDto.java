package com.kasztelanic.carcare.service.dto;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = { "id" })
@ToString(of = { "make", "model", "licensePlate" }, includeFieldNames = false)
public class VehicleDto {

     Long id;
     String make;
     String model;
     String licensePlate;
     FuelTypeDto fuelType;
     VehicleDetailsDto vehicleDetails;
}
