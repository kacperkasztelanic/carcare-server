package com.kasztelanic.carcare.service.impl;

import com.kasztelanic.carcare.service.EventService;
import com.kasztelanic.carcare.service.VehicleScopeService;
import com.kasztelanic.carcare.service.dto.ForthcomingEvent;
import com.kasztelanic.carcare.service.dto.ForthcomingEvent.EventType;
import com.kasztelanic.carcare.service.dto.InspectionDto;
import com.kasztelanic.carcare.service.dto.InsuranceDto;
import com.kasztelanic.carcare.service.dto.PeriodVehicle;
import com.kasztelanic.carcare.service.dto.RoutineServiceDto;
import com.kasztelanic.carcare.service.dto.VehicleRichDto;
import com.kasztelanic.carcare.service.mapper.VehicleRichMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.function.Function.identity;

@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    private final VehicleScopeService vehicleScopeService;
    private final VehicleRichMapper vehicleRichMapper;

    @Override
    public List<ForthcomingEvent> findForthcomingEvents(Collection<PeriodVehicle> periodVehicles) {
        Map<Long, PeriodVehicle> periodVehiclesByVehicleId = periodVehicles.stream()//
            // Keep the first period deliberately: a second period for the same vehicle is ignored.
            .collect(Collectors.toMap(PeriodVehicle::getVehicleId, identity(), (first, ignored) -> first));
        Set<Long> vehicleIds = periodVehicles.stream()//
            .map(PeriodVehicle::getVehicleId)//
            .collect(Collectors.toSet());
        return vehicleScopeService.findActiveOwnedVehicles(vehicleIds).stream()//
            .map(vehicleRichMapper::vehicleToVehicleDto)//
            .map(v -> findForthcomingEvents(periodVehiclesByVehicleId.get(v.getId()), v))//
            .flatMap(Collection::stream)//
            .sorted()//
            .collect(Collectors.toList());
    }

    private List<ForthcomingEvent> findForthcomingEvents(PeriodVehicle periodVehicle, VehicleRichDto vehicle) {
        return Stream.of(findForthcomingInspections(periodVehicle, vehicle.getInspection()),
                findForthcomingInsurances(periodVehicle, vehicle.getInsurance()),
                findForthcomingRoutineServices(periodVehicle, vehicle.getRoutineService()))//
            .flatMap(identity())//
            .sorted()//
            .collect(Collectors.toList());
    }

    private Stream<ForthcomingEvent> findForthcomingInsurances(PeriodVehicle periodVehicle,
                                                               Collection<InsuranceDto> insurances) {
        return insurances.stream()
            .filter(x -> !x.getValidThru().isBefore(periodVehicle.getDateFrom()) && !x.getValidThru()
                .isAfter(periodVehicle.getDateTo()))//
            .map(x -> ForthcomingEvent.builder()//
                .vehicleId(periodVehicle.getVehicleId())//
                .eventType(EventType.INSURANCE)//
                .details(x.getDetails())//
                .dateThru(x.getValidThru())//
                .mileageThru(0)//
                .build()//
            );
    }

    private Stream<ForthcomingEvent> findForthcomingInspections(PeriodVehicle periodVehicle,
                                                                Collection<InspectionDto> inspections) {
        return inspections.stream()
            .filter(x -> !x.getValidThru().isBefore(periodVehicle.getDateFrom()) && !x.getValidThru()
                .isAfter(periodVehicle.getDateTo()))//
            .map(x -> ForthcomingEvent.builder()//
                .vehicleId(periodVehicle.getVehicleId())//
                .eventType(EventType.INSPECTION)//
                .details(x.getDetails())//
                .dateThru(x.getValidThru())//
                .mileageThru(0)//
                .build()//
            );
    }

    private Stream<ForthcomingEvent> findForthcomingRoutineServices(PeriodVehicle periodVehicle,
                                                                    Collection<RoutineServiceDto> routineServices) {
        return routineServices.stream()//
            // `==  null ||` used to read as a null guard, but `&&` binds tighter, so it admitted
            // every dateless routine service into the window and then NPEd in the sort. A routine
            // service with no next date has nothing forthcoming: exclude it.
            .filter(x -> x.getNextByDate() != null && !x.getNextByDate().isBefore(periodVehicle.getDateFrom())//
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
