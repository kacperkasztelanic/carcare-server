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
        builder.id(insurance.getId());
        builder.costInCents(insurance.getCostInCents());
        builder.details(insurance.getDetails());
        builder.insuranceType(insurance.getInsuranceType().getType());
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
        builder.insuranceType(insuranceTypeRepository.findByType(insuranceDto.getInsuranceType())
                .orElseThrow(IllegalStateException::new));
        builder.insurer(insuranceDto.getInsurer());
        builder.number(insuranceDto.getNumber());
        builder.validFrom(insuranceDto.getValidFrom());
        builder.validThru(insuranceDto.getValidThru());
        builder.vehicleEvent(vehicleEventMapper.vehicleEventDtoToVehicleEvent(insuranceDto.getVehicleEvent()));
        return builder.build();
    }
}
