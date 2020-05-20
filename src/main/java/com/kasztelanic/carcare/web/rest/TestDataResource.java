package com.kasztelanic.carcare.web.rest;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kasztelanic.carcare.security.AuthoritiesConstants;
import com.kasztelanic.carcare.service.FuelAndInsuranceTypePopulator;
import com.kasztelanic.carcare.service.impl.RandomDataServiceImpl;

//TODO refactor
@RestController
@RequestMapping("/api/test-data")
@PreAuthorize("hasRole(\"" + AuthoritiesConstants.ADMIN + "\")")
public class TestDataResource {

    @Autowired
    private FuelAndInsuranceTypePopulator fuelAndInsuranceTypePopulator;
    @Autowired
    private RandomDataServiceImpl randomDataService;

    @Transactional
    @GetMapping("/populate-fuel-types")
    public boolean populateFuelTypes() {
        return fuelAndInsuranceTypePopulator.populateFuelTypes();
    }

    @Transactional
    @GetMapping("/populate-insurance-types")
    public boolean populateInsuranceTypes() {
        return fuelAndInsuranceTypePopulator.pupulateInsuranceTypes();
    }

    @Transactional
    @GetMapping("/random-vehicles/{numberOfVehicles}")
    public boolean createRandomVehiclesWithDataForCurrentUser(@PathVariable int numberOfVehicles) {
        return randomDataService.generate(numberOfVehicles);
    }
}
