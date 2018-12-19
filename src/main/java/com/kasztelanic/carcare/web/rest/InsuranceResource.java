package com.kasztelanic.carcare.web.rest;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kasztelanic.carcare.domain.Insurance;
import com.kasztelanic.carcare.domain.Vehicle;
import com.kasztelanic.carcare.repository.InsuranceRepository;
import com.kasztelanic.carcare.repository.VehicleRepository;
import com.kasztelanic.carcare.service.dto.InsuranceDto;
import com.kasztelanic.carcare.service.mapper.InsuranceMapper;

@RestController
@RequestMapping("/api/insurance")
public class InsuranceResource {

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private InsuranceRepository insuranceRepository;

    @Autowired
    private InsuranceMapper insuranceMapper;

    @Transactional
    @PutMapping("/add/{vehicleId}")
    public void addInsurance(@PathVariable long vehicleId, @RequestBody InsuranceDto insuranceDto) {
        Insurance insurance = insuranceMapper.insuranceDtoToInsurance(insuranceDto);
        Optional<Vehicle> vehicleOptional = vehicleRepository.findById(vehicleId);
        if (vehicleOptional.isPresent()) {
            Vehicle vehicle = vehicleOptional.get();
            insurance.setVehicle(vehicle);
            insuranceRepository.save(insurance);
        }
    }

    @Transactional
    @PostMapping("/edit/{insuranceId}")
    public InsuranceDto editInsurance(@PathVariable long insuranceId, @RequestBody InsuranceDto insuranceDto) {
        return insuranceRepository.findById(insuranceId)
                .map(i -> updateInsurance(i, insuranceMapper.insuranceDtoToInsurance(insuranceDto)))
                .map(insuranceRepository::save).map(insuranceMapper::insuranceToInsuranceDto)
                .orElseThrow(IllegalArgumentException::new);
    }

    @Transactional
    @DeleteMapping("/delete/{insuranceId}")
    public void deleteInsurance(@PathVariable long insuranceId) {
        insuranceRepository.deleteById(insuranceId);
    }

    @Transactional
    @GetMapping("/{vehicleId}")
    public List<InsuranceDto> getAllInsurances(@PathVariable long vehicleId) {
        return vehicleRepository.findById(vehicleId).map(v -> insuranceRepository.findByVehicle(v))
                .orElse(Collections.emptyList()).stream().map(insuranceMapper::insuranceToInsuranceDto)
                .collect(Collectors.toList());
    }

    private Insurance updateInsurance(Insurance insurance, Insurance updatedInsurance) {
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
