package com.kasztelanic.carcare.service.mapper;

import com.kasztelanic.carcare.domain.Insurance;
import com.kasztelanic.carcare.service.dto.InsuranceDto;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class InsuranceMapper {

    private final VehicleEventMapper vehicleEventMapper;
    private final InsuranceTypeMapper insuranceTypeMapper;

    @Autowired
    public InsuranceMapper(VehicleEventMapper vehicleEventMapper, InsuranceTypeMapper insuranceTypeMapper) {
        this.vehicleEventMapper = vehicleEventMapper;
        this.insuranceTypeMapper = insuranceTypeMapper;
    }

    public InsuranceDto insuranceToInsuranceDto(Insurance insurance) {
        return InsuranceDto.builder()//
                .id(insurance.getId())//
                .costInCents(insurance.getCostInCents())//
                .details(insurance.getDetails())//
                .insuranceType(insuranceTypeMapper.insuranceTypeToInsuranceTypeDto(insurance.getInsuranceType(),
                        Locale.forLanguageTag(insurance.getVehicle().getOwner().getLangKey())))//
                .insurer(insurance.getInsurer())//
                .number(insurance.getNumber())//
                .validFrom(insurance.getValidFrom())//
                .validThru(insurance.getValidThru())//
                .vehicleEvent(vehicleEventMapper.vehicleEventToVehicleEventDto(insurance.getVehicleEvent()))//
                .vehicleId(insurance.getVehicle().getId())//
                .build();
    }

    public Insurance insuranceDtoToInsurance(InsuranceDto insuranceDto) {
        return Insurance.builder()//
                .costInCents(insuranceDto.getCostInCents())//
                .details(insuranceDto.getDetails().trim())//
                .insuranceType(insuranceTypeMapper.insuranceTypeDtoToInsuranceType(insuranceDto.getInsuranceType()))//
                .insurer(insuranceDto.getInsurer().trim())//
                .number(insuranceDto.getNumber().trim())//
                .validFrom(insuranceDto.getValidFrom())//
                .validThru(insuranceDto.getValidThru())//
                .vehicleEvent(vehicleEventMapper.vehicleEventDtoToVehicleEvent(insuranceDto.getVehicleEvent()))//
                .build();
    }
}
