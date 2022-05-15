package com.kasztelanic.carcare.testdata;

import com.kasztelanic.carcare.service.dto.FuelTypeDto;
import com.kasztelanic.carcare.service.dto.VehicleDetailsDto;
import com.kasztelanic.carcare.service.dto.VehicleDetailsDto.VehicleDetailsDtoBuilder;
import com.kasztelanic.carcare.service.dto.VehicleDto;
import com.kasztelanic.carcare.service.dto.VehicleDto.VehicleDtoBuilder;
import org.apache.commons.lang3.RandomStringUtils;

import java.util.List;
import java.util.Random;

public class VehicleGenerator {

    private final Random random = new Random();

    private final List<FuelTypeDto> fuelTypes;

    public VehicleGenerator(List<FuelTypeDto> fuelTypes) {
        this.fuelTypes = fuelTypes;
    }

    public VehicleDto generate(VehicleTemplate template) {
        VehicleDtoBuilder builder = VehicleDto.builder();
        builder.make(template.getMake());
        builder.model(template.getModel());
        builder.fuelType(fuelTypes.get(random.nextInt(fuelTypes.size())));
        builder.licensePlate(randomLicensePlate());
        builder.vehicleDetails(prepareVehicleDetails(template));
        return builder.build();
    }

    private VehicleDetailsDto prepareVehicleDetails(VehicleTemplate template) {
        VehicleDetailsDtoBuilder builder = VehicleDetailsDto.builder();
        builder.modelSuffix(template.getModelSuffix());
        builder.weight(template.getWeight());
        int index = random.nextInt(Math.min(template.getEnginePower().length, template.getEngineVolumes().length));
        builder.enginePower(template.getEnginePower()[index]);
        builder.engineVolume(template.getEngineVolumes()[index]);
        builder.yearOfManufacture(template.getYearOfManufacture()[random.nextInt(template.getYearOfManufacture().length)]);
        builder.registrationCertificate(randomRegistrationCertificate());
        builder.vehicleCard(randomVehicleCard());
        builder.vinNumber(randomVinNumber());
        builder.notes("");
        return builder.build();
    }

    private String randomLicensePlate() {
        String[] letters = {"DW", "DWR", "DKL", "DTR", "DB", "WW", "PO", "OP"};
        StringBuilder builder = new StringBuilder();
        builder.append(letters[random.nextInt(letters.length)]);
        builder.append(" ");
        for (int i = 0; i < 4; i++) {
            builder.append(random.nextInt(10));
        }
        return builder.toString();
    }

    private String randomRegistrationCertificate() {
        return (RandomStringUtils.randomAlphabetic(2) + "/" + RandomStringUtils.randomAlphabetic(2)
            + RandomStringUtils.randomNumeric(9)).toUpperCase();
    }

    private String randomVehicleCard() {
        return (RandomStringUtils.randomAlphabetic(3) + RandomStringUtils.randomNumeric(7)).toUpperCase();
    }

    private String randomVinNumber() {
        return RandomStringUtils.randomAlphanumeric(17).toUpperCase();
    }
}
