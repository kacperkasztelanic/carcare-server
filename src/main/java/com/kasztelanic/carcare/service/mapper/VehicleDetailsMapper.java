package com.kasztelanic.carcare.service.mapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.kasztelanic.carcare.domain.Vehicle;
import com.kasztelanic.carcare.domain.VehicleDetails;
import com.kasztelanic.carcare.domain.VehicleDetails.VehicleDetailsBuilder;
import com.kasztelanic.carcare.service.ImageStorageService;
import com.kasztelanic.carcare.service.dto.VehicleDetailsDto;
import com.kasztelanic.carcare.service.dto.VehicleDetailsDto.VehicleDetailsDtoBuilder;

@Service
public class VehicleDetailsMapper {

    @Autowired
    private ImageStorageService imageStorageService;

    public VehicleDetailsDto vehicleDetailsToVehicleDetailsDto(Vehicle vehicle) {
        VehicleDetails vehicleDetails = vehicle.getVehicleDetails();
        VehicleDetailsDtoBuilder builder = VehicleDetailsDto.builder();
        builder.modelSuffix(vehicleDetails.getModelSuffix());
        builder.vinNumber(vehicleDetails.getVinNumber());
        builder.enginePower(vehicleDetails.getEnginePower());
        builder.engineVolume(vehicleDetails.getEngineVolume());
        builder.notes(vehicleDetails.getNotes());
        builder.registrationCertificate(vehicleDetails.getRegistrationCertificate());
        builder.vehicleCard(vehicleDetails.getVehicleCard());
        builder.weight(vehicleDetails.getWeight());
        builder.yearOfManufacture(vehicleDetails.getYearOfManufacture());
        builder.image(imageStorageService.load(vehicleDetails.getImage()));
        builder.imageContentType(vehicleDetails.getImageContentType());
        builder.vehicleId(vehicle.getId());
        return builder.build();
    }

    public VehicleDetails vehicleDetailsDtoToVehicleDetails(VehicleDetailsDto vehicleDetailsDto) {
        VehicleDetailsBuilder builder = VehicleDetails.builder();
        builder.modelSuffix(vehicleDetailsDto.getModelSuffix());
        builder.vinNumber(vehicleDetailsDto.getVinNumber());
        builder.enginePower(vehicleDetailsDto.getEnginePower());
        builder.engineVolume(vehicleDetailsDto.getEngineVolume());
        builder.notes(vehicleDetailsDto.getNotes());
        builder.registrationCertificate(vehicleDetailsDto.getRegistrationCertificate());
        builder.vehicleCard(vehicleDetailsDto.getVehicleCard());
        builder.weight(vehicleDetailsDto.getWeight());
        builder.yearOfManufacture(vehicleDetailsDto.getYearOfManufacture());
        builder.image(imageStorageService.save(vehicleDetailsDto.getImage(), vehicleDetailsDto.getImageContentType()));
        builder.imageContentType(vehicleDetailsDto.getImageContentType());
        return builder.build();
    }
}
