package com.kasztelanic.carcare.service.impl;

import com.kasztelanic.carcare.service.MileageService;
import com.kasztelanic.carcare.service.dto.HasVehicleEvent;
import com.kasztelanic.carcare.service.dto.MileageResult;
import com.kasztelanic.carcare.service.dto.PeriodVehicle;
import com.kasztelanic.carcare.service.dto.VehicleRichDto;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class MileageServiceImpl implements MileageService {

    //TODO refactor: ArrayList -> Stream
    @Override
    public MileageResult calculate(PeriodVehicle periodVehicle, VehicleRichDto vehicleRichDto) {
        List<HasVehicleEvent> events = new ArrayList<>();
        events.addAll(vehicleRichDto.getInspection());
        events.addAll(vehicleRichDto.getInsurance());
        events.addAll(vehicleRichDto.getRefuel());
        events.addAll(vehicleRichDto.getRepair());
        events.addAll(vehicleRichDto.getRoutineService());
        SortedMap<LocalDate, Integer> mileageByDate = events.stream()
            .filter(e -> !e.getVehicleEvent().getDate().isBefore(periodVehicle.getDateFrom())
                && !e.getVehicleEvent().getDate().isAfter(periodVehicle.getDateTo()))
            .sorted(Comparator.comparing(e -> e.getVehicleEvent().getMileage()))
            .collect(Collectors.toMap(e -> e.getVehicleEvent().getDate(), e -> e.getVehicleEvent().getMileage(),
                (v1, v2) -> v2, TreeMap::new));
        return MileageResult.of(periodVehicle, mileageByDate);
    }
}
