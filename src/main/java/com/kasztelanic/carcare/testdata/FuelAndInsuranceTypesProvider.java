package com.kasztelanic.carcare.testdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kasztelanic.carcare.domain.FuelType;
import com.kasztelanic.carcare.domain.InsuranceType;
import com.kasztelanic.carcare.service.dto.FuelTypeRequest;
import com.kasztelanic.carcare.service.dto.InsuranceTypeRequest;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class FuelAndInsuranceTypesProvider {

    private final ObjectMapper mapper;

    public FuelAndInsuranceTypesProvider(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public List<FuelType> deserializeFuelTypes(String json) throws IOException {
        return Arrays.stream(mapper.readValue(json, FuelTypeRequest[].class))
            .map(v -> FuelType.of(v.getType(), v.getEnglishTranslation(), v.getPolishTranslation()))
            .collect(Collectors.toList());
    }

    public String serializeFuelTypes(Collection<FuelTypeRequest> fuelTypes) throws IOException {
        return mapper.writeValueAsString(fuelTypes);
    }

    public List<InsuranceType> deserializeInsuranceTypes(String json) throws IOException {
        return Arrays.stream(mapper.readValue(json, InsuranceTypeRequest[].class))
            .map(v -> InsuranceType.of(v.getType(), v.getEnglishTranslation(), v.getPolishTranslation()))
            .collect(Collectors.toList());
    }

    public String serializeInsuranceTypes(Collection<InsuranceTypeRequest> insuranceTypes) throws IOException {
        return mapper.writeValueAsString(insuranceTypes);
    }
}
