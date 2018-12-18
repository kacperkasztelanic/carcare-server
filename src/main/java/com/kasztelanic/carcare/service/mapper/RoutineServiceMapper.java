package com.kasztelanic.carcare.service.mapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.kasztelanic.carcare.domain.RoutineService;
import com.kasztelanic.carcare.domain.RoutineService.RoutineServiceBuilder;
import com.kasztelanic.carcare.service.dto.RoutineServiceDto;
import com.kasztelanic.carcare.service.dto.RoutineServiceDto.RoutineServiceDtoBuilder;

@Service
public class RoutineServiceMapper {

    @Autowired
    private VehicleEventMapper vehicleEventMapper;

    public RoutineServiceDto routineServiceToRoutineServiceDto(RoutineService routineService) {
        RoutineServiceDtoBuilder builder = RoutineServiceDto.builder();
        builder.uuid(routineService.getUuid());
        builder.costInCents(routineService.getCostInCents());
        builder.details(routineService.getDetails());
        builder.nextByDate(routineService.getNextByDate());
        builder.nextByMileage(routineService.getNextByMileage());
        builder.station(routineService.getStation());
        builder.vehicleEvent(vehicleEventMapper.vehicleEventToVehicleEventDto(routineService.getVehicleEvent()));
        return builder.build();
    }

    public RoutineService routineServiceDtoToRoutineService(RoutineServiceDto routineServiceDto) {
        RoutineServiceBuilder builder = RoutineService.builder();
        builder.costInCents(routineServiceDto.getCostInCents());
        builder.details(routineServiceDto.getDetails());
        builder.nextByDate(routineServiceDto.getNextByDate());
        builder.nextByMileage(routineServiceDto.getNextByMileage());
        builder.station(routineServiceDto.getStation());
        builder.vehicleEvent(vehicleEventMapper.vehicleEventDtoToVehicleEvent(routineServiceDto.getVehicleEvent()));
        return builder.build();
    }
}
