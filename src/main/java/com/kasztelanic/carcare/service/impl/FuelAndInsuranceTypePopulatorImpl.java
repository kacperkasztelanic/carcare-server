package com.kasztelanic.carcare.service.impl;

import com.kasztelanic.carcare.domain.FuelType;
import com.kasztelanic.carcare.domain.InsuranceType;
import com.kasztelanic.carcare.repository.FuelTypeRepository;
import com.kasztelanic.carcare.repository.InsuranceTypeRepository;
import com.kasztelanic.carcare.service.FuelAndInsuranceTypePopulator;
import com.kasztelanic.carcare.testdata.FuelAndInsuranceTypesProvider;
import com.kasztelanic.carcare.testdata.ObjectMapperFactory;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

@Service
public class FuelAndInsuranceTypePopulatorImpl implements FuelAndInsuranceTypePopulator {

    private final Logger log = LoggerFactory.getLogger(FuelAndInsuranceTypePopulatorImpl.class);

    public static final String FUEL_TYPES_RESOURCE = "testdata/FuelTypes.json";
    public static final String INSURANCE_TYPES_RESOURCE = "testdata/InsuranceTypes.json";

    private final FuelAndInsuranceTypesProvider fuelAndInsuranceTypesProvider = new FuelAndInsuranceTypesProvider(ObjectMapperFactory.create());

    @Autowired
    private FuelTypeRepository fuelTypeRepository;
    @Autowired
    private InsuranceTypeRepository insuranceTypeRepository;

    @Override
    public boolean populateFuelTypes() {
        try {
            List<FuelType> fuelTypes = fuelAndInsuranceTypesProvider
                .deserializeFuelTypes(readJson(FUEL_TYPES_RESOURCE));
            fuelTypeRepository.saveAll(fuelTypes);
            return true;
        } catch (IOException e) {
            log.error("Could not populate fuel types");
            return false;
        }
    }

    @Override
    public boolean populateInsuranceTypes() {
        try {
            List<InsuranceType> insuranceTypes = fuelAndInsuranceTypesProvider
                .deserializeInsuranceTypes(readJson(INSURANCE_TYPES_RESOURCE));
            insuranceTypeRepository.saveAll(insuranceTypes);
            return true;
        } catch (IOException e) {
            log.error("Could not populate insurance types");
            return false;
        }
    }

    private String readJson(String resource) {
        try {
            return IOUtils.resourceToString(resource, Charset.forName("utf-8"), getClass().getClassLoader());
        } catch (IOException e) {
            log.error("Could not read: {}", resource);
            return "";
        }
    }
}
