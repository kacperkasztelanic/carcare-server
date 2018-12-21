package com.kasztelanic.carcare.web.rest;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kasztelanic.carcare.domain.InsuranceType;
import com.kasztelanic.carcare.repository.InsuranceTypeRepository;

@RestController
@RequestMapping("/api/insuranceType")
public class InsuranceTypeResource {

    @Autowired
    private InsuranceTypeRepository insuranceTypeRepository;

    @Transactional
    @PostMapping("/add/{type}")
    public void addInsuranceType(@PathVariable String type) {
        insuranceTypeRepository.save(InsuranceType.of(type.toUpperCase()));
    }
}
