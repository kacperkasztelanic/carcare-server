package com.kasztelanic.carcare.service;

import com.kasztelanic.carcare.service.dto.InsuranceDto;

import java.util.List;
import java.util.Optional;

public interface InsuranceService {

    Optional<InsuranceDto> getInsurance(Long id);

    List<InsuranceDto> getAllInsurances(Long vehicleId);

    Optional<InsuranceDto> addInsurance(Long vehicleId, InsuranceDto insuranceDto);

    Optional<InsuranceDto> editInsurance(Long id, InsuranceDto insuranceDto);

    Optional<InsuranceDto> deleteInsurance(Long id);
}
