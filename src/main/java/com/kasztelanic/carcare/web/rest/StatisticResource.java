package com.kasztelanic.carcare.web.rest;

import com.kasztelanic.carcare.service.StatisticService;
import com.kasztelanic.carcare.service.dto.AverageConsumptionResult;
import com.kasztelanic.carcare.service.dto.CostRequest;
import com.kasztelanic.carcare.service.dto.CostResult;
import com.kasztelanic.carcare.service.dto.MileageResult;
import com.kasztelanic.carcare.service.dto.PeriodVehicle;
import com.kasztelanic.carcare.web.rest.util.ResponseUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stats")
public class StatisticResource {

    private final StatisticService statisticService;

    @Autowired
    public StatisticResource(StatisticService statisticService) {
        this.statisticService = statisticService;
    }

    @PostMapping("/consumption/per-period")
    public ResponseEntity<AverageConsumptionResult> calculateAverageConsumptionPerPeriod(
            @RequestBody PeriodVehicle periodVehicle) {
        return ResponseEntity.ok(statisticService.calculateAverageConsumptionPerPeriod(periodVehicle));
    }

    @PostMapping("/consumption/per-refuel")
    public ResponseEntity<List<AverageConsumptionResult>> calculateAverageConsumptionPerRefuel(
            @RequestBody PeriodVehicle periodVehicle) {
        return ResponseUtil.createListOkResponse(statisticService.calculateAverageConsumptionPerRefuel(periodVehicle));
    }

    @PostMapping("/mileage")
    public ResponseEntity<MileageResult> calculateMileageStats(@RequestBody PeriodVehicle periodVehicle) {
        return statisticService.calculateMileageStats(periodVehicle)//
                .map(ResponseEntity::ok)//
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/cost")
    public ResponseEntity<List<CostResult>> calculate(@RequestBody CostRequest costRequest) {
        return ResponseUtil.createListOkResponse(statisticService.calculate(costRequest));
    }
}
