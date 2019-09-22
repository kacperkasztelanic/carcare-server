package com.kasztelanic.carcare.service.impl;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.kasztelanic.carcare.service.EventsService;
import com.kasztelanic.carcare.service.dto.ForthcomingEvent;
import com.kasztelanic.carcare.service.dto.ForthcomingEvent.EventType;
import com.kasztelanic.carcare.service.dto.InspectionDto;
import com.kasztelanic.carcare.service.dto.InsuranceDto;
import com.kasztelanic.carcare.service.dto.PeriodVehicle;
import com.kasztelanic.carcare.service.dto.RoutineServiceDto;
import com.kasztelanic.carcare.service.dto.VehicleRichDto;

@Service
public class EventsServiceImpl implements EventsService {

    @Override
    public List<ForthcomingEvent> findForthcomingEvents(PeriodVehicle periodVehicle, VehicleRichDto vehicle) {
        return Stream
                .of(findForthcomingInspections(periodVehicle, vehicle.getInspection()),
                        findForthcomingInsurances(periodVehicle, vehicle.getInsurance()),
                        findForthcomingRoutineServices(periodVehicle, vehicle.getRoutineService()))
                .flatMap(Function.identity()).sorted().collect(Collectors.toList());
    }

    private Stream<ForthcomingEvent> findForthcomingInsurances(PeriodVehicle periodVehicle,
            Collection<InsuranceDto> insurances) {
        return insurances.stream()
                .filter(x -> !x.getValidThru().isBefore(periodVehicle.getDateFrom())
                        && !x.getValidThru().isAfter(periodVehicle.getDateTo()))
                .map(x -> ForthcomingEvent.builder().vehicleId(periodVehicle.getVehicleId())
                        .eventType(EventType.INSURANCE).details(x.getDetails()).dateThru(x.getValidThru())
                        .mileageThru(0).build());
    }

    private Stream<ForthcomingEvent> findForthcomingInspections(PeriodVehicle periodVehicle,
            Collection<InspectionDto> inspections) {
        return inspections.stream()
                .filter(x -> !x.getValidThru().isBefore(periodVehicle.getDateFrom())
                        && !x.getValidThru().isAfter(periodVehicle.getDateTo()))
                .map(x -> ForthcomingEvent.builder().vehicleId(periodVehicle.getVehicleId())
                        .eventType(EventType.INSPECTION).details(x.getDetails()).dateThru(x.getValidThru())
                        .mileageThru(0).build());
    }

    private Stream<ForthcomingEvent> findForthcomingRoutineServices(PeriodVehicle periodVehicle,
        Collection<RoutineServiceDto> routineServices) {
        return routineServices.stream()//
            .filter(x -> x.getNextByDate() == null || !x.getNextByDate().isBefore(periodVehicle.getDateFrom())//
                && !x.getNextByDate().isAfter(periodVehicle.getDateTo()))//
            .map(x -> ForthcomingEvent.builder()//
                .vehicleId(periodVehicle.getVehicleId())//
                .eventType(EventType.SERVICE)//
                .details(x.getDetails())//
                .dateThru(x.getNextByDate())//
                .mileageThru(x.getNextByMileage())//
                .build()//
            );
    }
}
