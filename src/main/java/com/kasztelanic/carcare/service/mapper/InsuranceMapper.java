package com.kasztelanic.carcare.service.mapper;

import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.kasztelanic.carcare.domain.Insurance;
import com.kasztelanic.carcare.domain.Insurance.InsuranceBuilder;
import com.kasztelanic.carcare.service.dto.InsuranceDto;
import com.kasztelanic.carcare.service.dto.InsuranceDto.InsuranceDtoBuilder;

@Service
public class InsuranceMapper {

    @Autowired
    private VehicleEventMapper vehicleEventMapper;
    @Autowired
    private InsuranceTypeMapper insuranceTypeMapper;

    public InsuranceDto insuranceToInsuranceDto(Insurance insurance) {
        InsuranceDtoBuilder builder = InsuranceDto.builder();
        builder.id(insurance.getId());
        builder.costInCents(insurance.getCostInCents());
        builder.details(insurance.getDetails());
        builder.insuranceType(insuranceTypeMapper.insuranceTypeToInsuranceTypeDto(insurance.getInsuranceType(),
                Locale.forLanguageTag(insurance.getVehicle().getOwner().getLangKey())));
        builder.insurer(insurance.getInsurer());
        builder.number(insurance.getNumber());
        builder.validFrom(insurance.getValidFrom());
        builder.validThru(insurance.getValidThru());
        builder.vehicleEvent(vehicleEventMapper.vehicleEventToVehicleEventDto(insurance.getVehicleEvent()));
        builder.vehicleId(insurance.getVehicle().getId());
        return builder.build();
    }

    public Insurance insuranceDtoToInsurance(InsuranceDto insuranceDto) {
        InsuranceBuilder builder = Insurance.builder();
        builder.costInCents(insuranceDto.getCostInCents());
        builder.details(insuranceDto.getDetails());
        builder.insuranceType(insuranceTypeMapper.insuranceTypeDtoToInsuranceType(insuranceDto.getInsuranceType()));
        builder.insurer(insuranceDto.getInsurer());
        builder.number(insuranceDto.getNumber());
        builder.validFrom(insuranceDto.getValidFrom());
        builder.validThru(insuranceDto.getValidThru());
        builder.vehicleEvent(vehicleEventMapper.vehicleEventDtoToVehicleEvent(insuranceDto.getVehicleEvent()));
        return builder.build();
    }
}
