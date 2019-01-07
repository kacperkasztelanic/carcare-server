package com.kasztelanic.carcare.service.impl;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import com.kasztelanic.carcare.domain.User;
import com.kasztelanic.carcare.service.EventsService;
import com.kasztelanic.carcare.service.dto.ForthcomingEvent;
import com.kasztelanic.carcare.service.dto.InspectionDto;
import com.kasztelanic.carcare.service.dto.InsuranceDto;
import com.kasztelanic.carcare.service.dto.PeriodVehicle;
import com.kasztelanic.carcare.service.dto.RoutineServiceDto;
import com.kasztelanic.carcare.service.dto.VehicleRichDto;

@Service
public class EventsServiceImpl implements EventsService {

    @Autowired
    private MessageSource messageSource;

    @Override
    public List<ForthcomingEvent> findForthcomingEvents(PeriodVehicle periodVehicle, VehicleRichDto vehicle,
            User user) {
        Locale locale = Locale.forLanguageTag(user.getLangKey());
        return Stream
                .of(findForthcomingInspections(periodVehicle, vehicle.getInspection(),
                        messageSource.getMessage("entity.inspection.name", null, locale)),
                        findForthcomingInsurances(periodVehicle, vehicle.getInsurance(),
                                messageSource.getMessage("entity.insurance.name", null, locale)),
                        findForthcomingRoutineServices(periodVehicle, vehicle.getRoutineService(),
                                messageSource.getMessage("entity.routine-service.name", null, locale)))
                .flatMap(Function.identity()).sorted().collect(Collectors.toList());
    }

    private Stream<ForthcomingEvent> findForthcomingInsurances(PeriodVehicle periodVehicle,
            Collection<InsuranceDto> insurances, String type) {
        return insurances.stream()
                .filter(x -> !x.getValidThru().isBefore(periodVehicle.getDateFrom())
                        && !x.getValidThru().isAfter(periodVehicle.getDateTo()))
                .map(x -> ForthcomingEvent.builder().vehicleId(periodVehicle.getVehicleId()).type(type)
                        .details(x.getDetails()).dateThru(x.getValidThru()).mileageThru(0).build());
    }

    private Stream<ForthcomingEvent> findForthcomingInspections(PeriodVehicle periodVehicle,
            Collection<InspectionDto> inspections, String type) {
        return inspections.stream()
                .filter(x -> !x.getValidThru().isBefore(periodVehicle.getDateFrom())
                        && !x.getValidThru().isAfter(periodVehicle.getDateTo()))
                .map(x -> ForthcomingEvent.builder().vehicleId(periodVehicle.getVehicleId()).type(type)
                        .details(x.getDetails()).dateThru(x.getValidThru()).mileageThru(0).build());
    }

    private Stream<ForthcomingEvent> findForthcomingRoutineServices(PeriodVehicle periodVehicle,
            Collection<RoutineServiceDto> routineServices, String type) {
        return routineServices.stream()
                .filter(x -> !x.getNextByDate().isBefore(periodVehicle.getDateFrom())
                        && !x.getNextByDate().isAfter(periodVehicle.getDateTo()))
                .map(x -> ForthcomingEvent.builder().vehicleId(periodVehicle.getVehicleId()).type(type)
                        .details(x.getDetails()).dateThru(x.getNextByDate()).mileageThru(x.getNextByMileage()).build());
    }
}
