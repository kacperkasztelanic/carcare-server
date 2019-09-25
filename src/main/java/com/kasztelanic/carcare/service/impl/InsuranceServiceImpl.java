package com.kasztelanic.carcare.service.impl;

import com.kasztelanic.carcare.domain.Insurance;
import com.kasztelanic.carcare.repository.InsuranceRepository;
import com.kasztelanic.carcare.repository.VehicleRepository;
import com.kasztelanic.carcare.service.InsuranceService;
import com.kasztelanic.carcare.service.dto.InsuranceDto;
import com.kasztelanic.carcare.service.mapper.InsuranceMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class InsuranceServiceImpl implements InsuranceService {

    private final VehicleRepository vehicleRepository;
    private final InsuranceRepository insuranceRepository;
    private final InsuranceMapper insuranceMapper;

    @Autowired
    public InsuranceServiceImpl(VehicleRepository vehicleRepository, InsuranceRepository insuranceRepository,
            InsuranceMapper insuranceMapper) {
        this.vehicleRepository = vehicleRepository;
        this.insuranceRepository = insuranceRepository;
        this.insuranceMapper = insuranceMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InsuranceDto> getInsurance(Long id) {
        return insuranceRepository.findByIdAndOwnerIsCurrentUser(id)//
                .map(insuranceMapper::insuranceToInsuranceDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InsuranceDto> getAllInsurances(Long vehicleId) {
        return insuranceRepository.findByVehicleIdAndOwnerIsCurrentUser(vehicleId).stream()
                .map(insuranceMapper::insuranceToInsuranceDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public Optional<InsuranceDto> addInsurance(Long vehicleId, InsuranceDto insuranceDto) {
        return vehicleRepository.findByIdAndOwnerIsCurrentUser(vehicleId)//
                .map(v -> insuranceMapper.insuranceDtoToInsurance(insuranceDto).setVehicle(v))//
                .map(insuranceRepository::save)//
                .map(insuranceMapper::insuranceToInsuranceDto);
    }

    @Override
    @Transactional
    public Optional<InsuranceDto> editInsurance(Long id, InsuranceDto insuranceDto) {
        return insuranceRepository.findByIdAndOwnerIsCurrentUser(id)//
                .map(i -> updateInsurance(i, insuranceMapper.insuranceDtoToInsurance(insuranceDto)))//
                .map(insuranceRepository::save)//
                .map(insuranceMapper::insuranceToInsuranceDto);
    }

    @Override
    @Transactional
    public Optional<InsuranceDto> deleteInsurance(Long id) {
        Optional<InsuranceDto> insurance = insuranceRepository.findByIdAndOwnerIsCurrentUser(id)//
                .map(insuranceMapper::insuranceToInsuranceDto);
        insurance.ifPresent(i -> insuranceRepository.deleteById(id));
        return insurance;
    }

    private static Insurance updateInsurance(Insurance insurance, Insurance updatedInsurance) {
        insurance.setCostInCents(updatedInsurance.getCostInCents());
        insurance.setDetails(updatedInsurance.getDetails());
        insurance.setInsuranceType(updatedInsurance.getInsuranceType());
        insurance.setInsurer(updatedInsurance.getInsurer());
        insurance.setNumber(updatedInsurance.getNumber());
        insurance.setValidFrom(updatedInsurance.getValidFrom());
        insurance.setValidThru(updatedInsurance.getValidThru());
        insurance.setVehicleEvent(updatedInsurance.getVehicleEvent());
        return insurance;
    }
}
