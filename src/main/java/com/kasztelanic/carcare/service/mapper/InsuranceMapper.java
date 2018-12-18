package com.kasztelanic.carcare.service.mapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.kasztelanic.carcare.domain.Insurance;
import com.kasztelanic.carcare.domain.Insurance.InsuranceBuilder;
import com.kasztelanic.carcare.repository.InsuranceTypeRepository;
import com.kasztelanic.carcare.service.dto.InsuranceDto;
import com.kasztelanic.carcare.service.dto.InsuranceDto.InsuranceDtoBuilder;

@Service
public class InsuranceMapper {

    @Autowired
    private VehicleEventMapper vehicleEventMapper;

    @Autowired
    private InsuranceTypeRepository insuranceTypeRepository;

    public InsuranceDto insuranceToInsuranceDto(Insurance insurance) {
        InsuranceDtoBuilder builder = InsuranceDto.builder();
        builder.uuid(insurance.getUuid());
        builder.costInCents(insurance.getCostInCents());
        builder.details(insurance.getDetails());
        builder.insuranceType(insurance.getInsuranceType().toString());
        builder.insurer(insurance.getInsurer());
        builder.number(insurance.getNumber());
        builder.validFrom(insurance.getValidFrom());
        builder.validThru(insurance.getValidThru());
        builder.vehicleEvent(vehicleEventMapper.vehicleEventToVehicleEventDto(insurance.getVehicleEvent()));
        return builder.build();
    }

    public Insurance insuranceDtoToInsurance(InsuranceDto insurance) {
        InsuranceBuilder builder = Insurance.builder();
        builder.costInCents(insurance.getCostInCents());
        builder.details(insurance.getDetails());
        builder.insuranceType(insuranceTypeRepository.findByType(insurance.getInsuranceType())
                .orElseThrow(IllegalStateException::new));
        builder.insurer(insurance.getInsurer());
        builder.number(insurance.getNumber());
        builder.validFrom(insurance.getValidFrom());
        builder.validThru(insurance.getValidThru());
        return builder.build();
    }
}
