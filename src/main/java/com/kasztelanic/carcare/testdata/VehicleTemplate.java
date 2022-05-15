package com.kasztelanic.carcare.testdata;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Builder
@EqualsAndHashCode(of = {"make", "model", "modelSuffix"})
@ToString(of = {"make", "model", "modelSuffix"}, includeFieldNames = false)
public class VehicleTemplate {

    @Getter
    private final String make;
    @Getter
    private final String model;
    @Getter
    private final String modelSuffix;
    @Getter
    private final int[] engineVolumes;
    @Getter
    private final int[] enginePower;
    @Getter
    private final int[] yearOfManufacture;
    @Getter
    private final int weight;
    @Getter
    private final int fuelConsumption;
}
