package com.kasztelanic.carcare.web.rest;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kasztelanic.carcare.domain.Insurance;
import com.kasztelanic.carcare.repository.InsuranceRepository;
import com.kasztelanic.carcare.repository.VehicleRepository;
import com.kasztelanic.carcare.service.dto.InsuranceDto;
import com.kasztelanic.carcare.service.mapper.InsuranceMapper;
import com.kasztelanic.carcare.web.rest.util.HeaderUtil;
import com.kasztelanic.carcare.web.rest.util.URIUtil;

@RestController
@RequestMapping("/api/insurance")
public class InsuranceResource {

    private static final String ENTITY_NAME = "insurance";

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private InsuranceRepository insuranceRepository;

    @Autowired
    private InsuranceMapper insuranceMapper;

    @Transactional
    @PostMapping("/{vehicleId}")
    public ResponseEntity<InsuranceDto> addInsurance(@PathVariable Long vehicleId,
            @RequestBody InsuranceDto insuranceDto) {
        Insurance insurance = insuranceMapper.insuranceDtoToInsurance(insuranceDto);
        return vehicleRepository.findByIdAndOwnerIsCurrentUser(vehicleId).map(insurance::setVehicle)
                .map(insuranceRepository::save).map(insuranceMapper::insuranceToInsuranceDto)
                .map(i -> ResponseEntity
                        .created(URIUtil.buildURI(
                                String.format("/api/insurance/%s/%s", vehicleId.toString(), i.getId().toString())))
                        .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, i.getId().toString())).body(i))
                .orElse(ResponseEntity.notFound().build());
    }

    @Transactional
    @PutMapping("/{insuranceId}")
    public ResponseEntity<InsuranceDto> editInsurance(@PathVariable Long insuranceId,
            @RequestBody InsuranceDto insuranceDto) {
        return insuranceRepository.findByIdAndOwnerIsCurrentUser(insuranceId)
                .map(i -> updateInsurance(i, insuranceMapper.insuranceDtoToInsurance(insuranceDto)))
                .map(insuranceRepository::save).map(insuranceMapper::insuranceToInsuranceDto)
                .map(i -> ResponseEntity.ok()
                        .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, i.getId().toString())).body(i))
                .orElse(ResponseEntity.notFound().build());
    }

    @Transactional
    @DeleteMapping("/{insuranceId}")
    public ResponseEntity<Void> deleteInsurance(@PathVariable Long insuranceId) {
        Optional<Insurance> insurance = insuranceRepository.findByIdAndOwnerIsCurrentUser(insuranceId);
        if (insurance.isPresent()) {
            insuranceRepository.delete(insurance.get());
            return ResponseEntity.ok()
                    .headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, insuranceId.toString())).build();
        }
        return ResponseEntity.notFound().build();
    }

    @Transactional
    @GetMapping("/all/{vehicleId}")
    public List<InsuranceDto> getAllInsurances(@PathVariable Long vehicleId) {
        return insuranceRepository.findByVehicleIdAndOwnerIsCurrentUser(vehicleId).stream()
                .map(insuranceMapper::insuranceToInsuranceDto).collect(Collectors.toList());
    }

    @Transactional
    @GetMapping("/{insuranceId}")
    public ResponseEntity<InsuranceDto> getInsurance(@PathVariable Long insuranceId) {
        return insuranceRepository.findByIdAndOwnerIsCurrentUser(insuranceId)
                .map(insuranceMapper::insuranceToInsuranceDto).map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
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
