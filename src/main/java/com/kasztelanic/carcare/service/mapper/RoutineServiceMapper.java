package com.kasztelanic.carcare.service.mapper;

import com.kasztelanic.carcare.domain.RoutineService;
import com.kasztelanic.carcare.service.dto.RoutineServiceDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RoutineServiceMapper {

    private final VehicleEventMapper vehicleEventMapper;

    @Autowired
    public RoutineServiceMapper(VehicleEventMapper vehicleEventMapper) {
        this.vehicleEventMapper = vehicleEventMapper;
    }

    public RoutineServiceDto routineServiceToRoutineServiceDto(RoutineService routineService) {
        return RoutineServiceDto.builder()//
            .id(routineService.getId())//
            .costInCents(routineService.getCostInCents())//
            .details(routineService.getDetails())//
            .nextByDate(routineService.getNextByDate())//
            .nextByMileage(routineService.getNextByMileage())//
            .station(routineService.getStation())//
            .vehicleEvent(vehicleEventMapper.vehicleEventToVehicleEventDto(routineService.getVehicleEvent()))//
            .vehicleId(routineService.getVehicle().getId())//
            .build();
    }

    public RoutineService routineServiceDtoToRoutineService(RoutineServiceDto routineServiceDto) {
        return RoutineService.builder()//
            .costInCents(routineServiceDto.getCostInCents())//
            .details(routineServiceDto.getDetails().trim())//
            .nextByDate(routineServiceDto.getNextByDate())//
            .nextByMileage(routineServiceDto.getNextByMileage())//
            .station(routineServiceDto.getStation().trim())//
            .vehicleEvent(vehicleEventMapper.vehicleEventDtoToVehicleEvent(routineServiceDto.getVehicleEvent()))//
            .build();
    }
}
