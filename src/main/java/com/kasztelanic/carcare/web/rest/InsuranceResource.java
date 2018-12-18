package com.kasztelanic.carcare.web.rest;

import java.util.Optional;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kasztelanic.carcare.domain.Insurance;
import com.kasztelanic.carcare.domain.Vehicle;
import com.kasztelanic.carcare.domain.VehicleEvent;
import com.kasztelanic.carcare.repository.InsuranceTypeRepository;
import com.kasztelanic.carcare.repository.VehicleRepository;
import com.kasztelanic.carcare.service.dto.InsuranceDto;

@RestController
@RequestMapping("/api/insurance")
public class InsuranceResource {

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private InsuranceTypeRepository insuranceTypeRepository;

    @Transactional
    @PutMapping("/add/{vehicleId}")
    public void addInsurance(@PathVariable long vehicleId, @RequestBody InsuranceDto insuranceDto) {
        Insurance.InsuranceBuilder builder = Insurance.builder();
        builder.costInCents(insuranceDto.getCostInCents());
        builder.details(insuranceDto.getDetails());
        builder.insuranceType(insuranceTypeRepository.findByType(insuranceDto.getInsuranceType())
                .orElseThrow(IllegalArgumentException::new));
        builder.insurer(insuranceDto.getInsurer());
        builder.number(insuranceDto.getNumber());
        builder.validFrom(insuranceDto.getValidFrom());
        builder.validThru(insuranceDto.getValidThru());
        builder.vehicleEvent(
                VehicleEvent.of(insuranceDto.getVehicleEvent().getMileage(), insuranceDto.getVehicleEvent().getDate()));
        Insurance insurance = builder.build();
        Optional<Vehicle> vehicleOptional = vehicleRepository.findById(vehicleId);
        if (vehicleOptional.isPresent()) {
            Vehicle vehicle = vehicleOptional.get();
            vehicle.addInsurance(insurance);
        }
    }
}
