package com.kasztelanic.carcare.web.rest;

import java.util.List;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kasztelanic.carcare.repository.VehicleRepository;
import com.kasztelanic.carcare.service.CostCalculator;
import com.kasztelanic.carcare.service.dto.CostRequest;
import com.kasztelanic.carcare.service.dto.CostResult;
import com.kasztelanic.carcare.service.dto.PeriodVehicle;
import com.kasztelanic.carcare.service.mapper.VehicleMapper;

@RestController
@RequestMapping("/api/cost")
public class CostResource {

    @Autowired
    private CostCalculator costCalculator;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private VehicleMapper vehicleMapper;

    @Transactional
    @PostMapping("")
    public List<CostResult> calculate(@RequestBody CostRequest costRequest) {
        return vehicleRepository.findAllByIdAndOwnerIsCurrentUser(costRequest.getVehicleIds()).stream()
                .map(vehicleMapper::vehicleToVehicleDto)
                .map(v -> costCalculator
                        .calculate(PeriodVehicle.of(v.getId(), costRequest.getDateFrom(), costRequest.getDateTo()), v))
                .collect(Collectors.toList());
    }
}
