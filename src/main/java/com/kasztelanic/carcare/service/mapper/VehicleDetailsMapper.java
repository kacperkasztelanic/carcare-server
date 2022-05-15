package com.kasztelanic.carcare.service.mapper;

import com.kasztelanic.carcare.domain.Vehicle;
import com.kasztelanic.carcare.domain.VehicleDetails;
import com.kasztelanic.carcare.service.ImageStorageService;
import com.kasztelanic.carcare.service.dto.VehicleDetailsDto;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class VehicleDetailsMapper {

    private final ImageStorageService imageStorageService;
    private final Tika tika = new Tika();

    @Autowired
    public VehicleDetailsMapper(ImageStorageService imageStorageService) {
        this.imageStorageService = imageStorageService;
    }

    public VehicleDetailsDto vehicleDetailsToVehicleDetailsDto(Vehicle vehicle) {
        VehicleDetails vehicleDetails = vehicle.getVehicleDetails();
        return VehicleDetailsDto.builder()//
            .modelSuffix(vehicleDetails.getModelSuffix())//
            .vinNumber(vehicleDetails.getVinNumber())//
            .enginePower(vehicleDetails.getEnginePower())//
            .engineVolume(vehicleDetails.getEngineVolume())//
            .notes(vehicleDetails.getNotes())//
            .registrationCertificate(vehicleDetails.getRegistrationCertificate())//
            .vehicleCard(vehicleDetails.getVehicleCard())//
            .weight(vehicleDetails.getWeight())//
            .yearOfManufacture(vehicleDetails.getYearOfManufacture())//
            .image(imageStorageService.load(vehicleDetails.getImage()))//
            .imageContentType(tika.detect(vehicleDetails.getImage()))//
            .vehicleId(vehicle.getId())//
            .build();
    }

    public VehicleDetails vehicleDetailsDtoToVehicleDetails(VehicleDetailsDto vehicleDetailsDto) {
        return VehicleDetails.builder()//
            .modelSuffix(vehicleDetailsDto.getModelSuffix().trim())//
            .vinNumber(vehicleDetailsDto.getVinNumber().trim())//
            .enginePower(vehicleDetailsDto.getEnginePower())//
            .engineVolume(vehicleDetailsDto.getEngineVolume())//
            .notes(vehicleDetailsDto.getNotes().trim())//
            .registrationCertificate(vehicleDetailsDto.getRegistrationCertificate().trim())//
            .vehicleCard(vehicleDetailsDto.getVehicleCard().trim())//
            .weight(vehicleDetailsDto.getWeight())//
            .yearOfManufacture(vehicleDetailsDto.getYearOfManufacture())//
            .image(imageStorageService
                .save(vehicleDetailsDto.getImage(), vehicleDetailsDto.getImageContentType()))//
            .build();
    }
}
