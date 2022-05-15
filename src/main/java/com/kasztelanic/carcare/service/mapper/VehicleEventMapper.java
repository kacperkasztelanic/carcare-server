package com.kasztelanic.carcare.service.mapper;

import com.kasztelanic.carcare.domain.VehicleEvent;
import com.kasztelanic.carcare.service.dto.VehicleEventDto;
import org.springframework.stereotype.Service;

@Service
public class VehicleEventMapper {

    public VehicleEventDto vehicleEventToVehicleEventDto(VehicleEvent vehicleEvent) {
        return VehicleEventDto.of(vehicleEvent.getMileage(), vehicleEvent.getDate());
    }

    public VehicleEvent vehicleEventDtoToVehicleEvent(VehicleEventDto vehicleEventDto) {
        return VehicleEvent.of(vehicleEventDto.getMileage(), vehicleEventDto.getDate());
    }
}
