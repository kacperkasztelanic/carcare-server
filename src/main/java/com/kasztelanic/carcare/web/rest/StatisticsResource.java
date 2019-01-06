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
import com.kasztelanic.carcare.repository.VehicleRepository;
import com.kasztelanic.carcare.service.AverageConsumptionCalculator;
import com.kasztelanic.carcare.service.MileageService;
import com.kasztelanic.carcare.service.dto.AverageConsumptionResult;
import com.kasztelanic.carcare.service.dto.MileageResult;
import com.kasztelanic.carcare.service.dto.PeriodVehicle;
import com.kasztelanic.carcare.service.dto.RefuelDto;
import com.kasztelanic.carcare.service.mapper.RefuelMapper;
import com.kasztelanic.carcare.service.mapper.VehicleRichMapper;
import com.kasztelanic.carcare.web.rest.util.ResponseUtil;

@RestController
@RequestMapping("/api/stats")
public class StatisticsResource {

    @Autowired
    private AverageConsumptionCalculator averageConsumptionCalculator;
    @Autowired
    private MileageService mileageService;
    @Autowired
    private VehicleRepository vehicleRepository;
    @Autowired
    private VehicleRichMapper vehicleRichMapper;
    @Autowired
    private RefuelRepository refuelRepository;
    @Autowired
    private RefuelMapper refuelMapper;

    @Transactional
    @PostMapping("/consumption/per-period")
    public ResponseEntity<AverageConsumptionResult> calculateAverageConsumptionPerPeriod(
            @RequestBody PeriodVehicle periodVehicle) {
        List<RefuelDto> refuels = refuelRepository.findByVehicleIdAndOwnerIsCurrentUser(periodVehicle.getVehicleId())
                .stream().map(refuelMapper::refuelToRefuelDto).collect(Collectors.toList());
        AverageConsumptionResult result = averageConsumptionCalculator.calculate(periodVehicle, refuels);
        return ResponseEntity.ok(result);
    }

    @Transactional
    @PostMapping("/consumption/per-refuel")
    public ResponseEntity<List<AverageConsumptionResult>> calculateAverageConsumptionPerRefuel(
            @RequestBody PeriodVehicle periodVehicle) {
        List<RefuelDto> refuels = refuelRepository.findByVehicleIdAndOwnerIsCurrentUser(periodVehicle.getVehicleId())
                .stream().map(refuelMapper::refuelToRefuelDto).collect(Collectors.toList());
        List<AverageConsumptionResult> result = averageConsumptionCalculator.calculatePerRefuel(periodVehicle, refuels);
        return ResponseUtil.createListOkResponse(result);
    }

    @Transactional
    @PostMapping("/mileage")
    public ResponseEntity<MileageResult> calculateMileageStats(@RequestBody PeriodVehicle periodVehicle) {
        return io.github.jhipster.web.util.ResponseUtil.wrapOrNotFound(vehicleRepository
                .findByIdAndOwnerIsCurrentUser(periodVehicle.getVehicleId()).map(vehicleRichMapper::vehicleToVehicleDto)
                .map(v -> mileageService.calculate(periodVehicle, v)));
    }
}
