package com.kasztelanic.carcare.service.dto;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Getter
@Builder
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class AdminVehicleDto {

    Long id;
    String ownerLogin;
    String make;
    String model;
    String licensePlate;
    Instant archivedAt;
}
