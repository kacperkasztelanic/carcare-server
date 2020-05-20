package com.kasztelanic.carcare.service.impl;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kasztelanic.carcare.domain.Vehicle;
import com.kasztelanic.carcare.repository.VehicleRepository;
import com.kasztelanic.carcare.service.UserService;
import com.kasztelanic.carcare.service.dto.FuelTypeDto;
import com.kasztelanic.carcare.service.dto.VehicleDto;
import com.kasztelanic.carcare.service.mapper.FuelTypeMapper;
import com.kasztelanic.carcare.service.mapper.VehicleMapper;
import com.kasztelanic.carcare.testdata.FuelAndInsuranceTypesProvider;
import com.kasztelanic.carcare.testdata.ObjectMapperFactory;
import com.kasztelanic.carcare.testdata.VehicleGenerator;
import com.kasztelanic.carcare.testdata.VehicleTemplate;

//TODO move to testdata
@Service
public class RandomDataServiceImpl {

    private Logger log = LoggerFactory.getLogger(RandomDataServiceImpl.class);

    private static final String FUEL_TYPES_RESOURCE = FuelAndInsuranceTypePopulatorImpl.FUEL_TYPES_RESOURCE;
    private static final String VEHICLE_TEMPLATES_RESOURCE = "testdata/VehicleTemplates.json";

    @Autowired
    private FuelTypeMapper fuelTypeMapper;
    @Autowired
    private VehicleRepository vehicleRepository;
    @Autowired
    private VehicleMapper vehicleMapper;
    @Autowired
    private UserService userService;

    public boolean generate(int numberOfVehicles) {
        VehicleGenerator vehicleGenerator = new VehicleGenerator(prepareFuelTypes());
        List<VehicleTemplate> vehicleTemplates = prepareVehicleTemplates(ObjectMapperFactory.create());
        Collections.shuffle(vehicleTemplates);
        return vehicleTemplates.stream().limit(numberOfVehicles).map(i -> generateOne(vehicleGenerator, i))
                .count() == numberOfVehicles;
    }

    private boolean generateOne(VehicleGenerator generator, VehicleTemplate template) {
        VehicleDto vehicleDto = generator.generate(template);
        Vehicle vehicle = vehicleMapper.vehicleDtoToVehicle(vehicleDto);
        vehicle.setOwner(userService.getUserWithAuthorities().orElseThrow(IllegalStateException::new));
        vehicleRepository.save(vehicle);
        return true;
    }

    private List<FuelTypeDto> prepareFuelTypes() {
        Locale locale = Locale.forLanguageTag(
                userService.getUserWithAuthorities().orElseThrow(IllegalStateException::new).getLangKey());
        FuelAndInsuranceTypesProvider provider = new FuelAndInsuranceTypesProvider(ObjectMapperFactory.create());
        try {
            return provider
                    .deserializeFuelTypes(IOUtils.resourceToString(FUEL_TYPES_RESOURCE, Charset.forName("utf-8"),
                            getClass().getClassLoader()))
                    .stream().map(i -> fuelTypeMapper.fuelTypeToFuelTypeDto(i, locale)).collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Could not prepare fuel types");
            return Collections.emptyList();
        }
    }

    private List<VehicleTemplate> prepareVehicleTemplates(ObjectMapper mapper) {
        try {
            return Arrays.asList(mapper.readValue(IOUtils.resourceToString(VEHICLE_TEMPLATES_RESOURCE,
                    Charset.forName("utf-8"), getClass().getClassLoader()), VehicleTemplate[].class));
        } catch (IOException e) {
            log.error("Could not prepare vehicle templates");
            return Collections.emptyList();
        }
    }
}
