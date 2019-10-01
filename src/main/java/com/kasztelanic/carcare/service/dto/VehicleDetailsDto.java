package com.kasztelanic.carcare.service.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Builder
@ToString
public class VehicleDetailsDto {

    @Getter
    private final String modelSuffix;
    @Getter
    private final String vinNumber;
    @Getter
    private final String vehicleCard;
    @Getter
    private final String registrationCertificate;
    @Getter
    private final Integer yearOfManufacture;
    @Getter
    private final Integer engineVolume;
    @Getter
    private final Integer enginePower;
    @Getter
    private final Integer weight;
    @Getter
    private final String notes;
    @Getter
    private final byte[] image;
    @Getter
    private final String imageContentType;
    @Getter
    private final Long vehicleId;

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
