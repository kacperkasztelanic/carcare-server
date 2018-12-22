package com.kasztelanic.carcare.web.rest;

import java.util.List;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kasztelanic.carcare.repository.RefuelRepository;
import com.kasztelanic.carcare.service.AverageConsumptionCalculator;
import com.kasztelanic.carcare.service.dto.AverageConsumptionResult;
import com.kasztelanic.carcare.service.dto.PeriodVehicle;
import com.kasztelanic.carcare.service.dto.RefuelDto;
import com.kasztelanic.carcare.service.mapper.RefuelMapper;

@RestController
@RequestMapping("/api/consumption")
public class ConsumptionResource {

    @Autowired
    private AverageConsumptionCalculator averageConsumptionCalculator;

    @Autowired
    private RefuelRepository refuelRepository;

    @Autowired
    private RefuelMapper refuelMapper;

    @Transactional
    @PostMapping("")
    public ResponseEntity<AverageConsumptionResult> calculate(@RequestBody PeriodVehicle periodVehicle) {
        List<RefuelDto> refuels = refuelRepository.findByVehicleIdAndOwnerIsCurrentUser(periodVehicle.getVehicleId())
                .stream().map(refuelMapper::refuelToRefuelDto).collect(Collectors.toList());
        AverageConsumptionResult result = averageConsumptionCalculator.calculate(periodVehicle, refuels);
        return ResponseEntity.ok(result);
    }
}
