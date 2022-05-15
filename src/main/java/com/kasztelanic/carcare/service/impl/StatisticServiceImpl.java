package com.kasztelanic.carcare.service.impl;

import com.kasztelanic.carcare.repository.RefuelRepository;
import com.kasztelanic.carcare.repository.VehicleRepository;
import com.kasztelanic.carcare.service.AverageConsumptionCalculator;
import com.kasztelanic.carcare.service.CostCalculator;
import com.kasztelanic.carcare.service.MileageService;
import com.kasztelanic.carcare.service.StatisticService;
import com.kasztelanic.carcare.service.dto.AverageConsumptionResult;
import com.kasztelanic.carcare.service.dto.CostRequest;
import com.kasztelanic.carcare.service.dto.CostResult;
import com.kasztelanic.carcare.service.dto.MileageResult;
import com.kasztelanic.carcare.service.dto.PeriodVehicle;
import com.kasztelanic.carcare.service.dto.RefuelDto;
import com.kasztelanic.carcare.service.mapper.RefuelMapper;
import com.kasztelanic.carcare.service.mapper.VehicleRichMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatisticServiceImpl implements StatisticService {

    private final AverageConsumptionCalculator averageConsumptionCalculator;
    private final MileageService mileageService;
    private final CostCalculator costCalculator;
    private final VehicleRepository vehicleRepository;
    private final VehicleRichMapper vehicleRichMapper;
    private final RefuelRepository refuelRepository;
    private final RefuelMapper refuelMapper;

    @Override
    @Transactional(readOnly = true)
    public AverageConsumptionResult calculateAverageConsumptionPerPeriod(PeriodVehicle periodVehicle) {
        List<RefuelDto> refuels = refuelRepository.findByVehicleIdAndOwnerIsCurrentUser(periodVehicle.getVehicleId())
            .stream()//
            .map(refuelMapper::refuelToRefuelDto)//
            .collect(Collectors.toList());
        return averageConsumptionCalculator.calculate(periodVehicle, refuels);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AverageConsumptionResult> calculateAverageConsumptionPerRefuel(PeriodVehicle periodVehicle) {
        List<RefuelDto> refuels = refuelRepository.findByVehicleIdAndOwnerIsCurrentUser(periodVehicle.getVehicleId())
            .stream()//
            .map(refuelMapper::refuelToRefuelDto)//
            .collect(Collectors.toList());
        return averageConsumptionCalculator.calculatePerRefuel(periodVehicle, refuels);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MileageResult> calculateMileageStats(PeriodVehicle periodVehicle) {
        return vehicleRepository.findByIdAndOwnerIsCurrentUser(periodVehicle.getVehicleId())//
            .map(vehicleRichMapper::vehicleToVehicleDto)//
            .map(v -> mileageService.calculate(periodVehicle, v));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CostResult> calculate(CostRequest costRequest) {
        return vehicleRepository.findAllByIdAndOwnerIsCurrentUser(costRequest.getVehicleIds()).stream()//
            .map(vehicleRichMapper::vehicleToVehicleDto)//
            .map(v -> costCalculator
                .calculate(PeriodVehicle.of(v.getId(), costRequest.getDateFrom(), costRequest.getDateTo()),
                    v))//
            .collect(Collectors.toList());
    }
}
