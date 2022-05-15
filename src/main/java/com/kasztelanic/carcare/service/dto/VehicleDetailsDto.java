package com.kasztelanic.carcare.service.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class VehicleDetailsDto {

    String modelSuffix;
    String vinNumber;
    String vehicleCard;
    String registrationCertificate;
    Integer yearOfManufacture;
    Integer engineVolume;
    Integer enginePower;
    Integer weight;
    String notes;
    byte[] image;
    String imageContentType;
    Long vehicleId;

    public static VehicleDetailsDtoBuilder defaultBuilder() {
        return VehicleDetailsDto.builder()//
            .modelSuffix("")//
            .vinNumber("")//
            .vehicleCard("")//
            .registrationCertificate("")//
            .yearOfManufacture(0)//
            .enginePower(0)//
            .engineVolume(0)//
            .weight(0)//
            .notes("");
    }
}
