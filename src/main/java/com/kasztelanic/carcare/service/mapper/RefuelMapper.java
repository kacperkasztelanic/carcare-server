package com.kasztelanic.carcare.service.mapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.kasztelanic.carcare.domain.Refuel;
import com.kasztelanic.carcare.domain.Refuel.RefuelBuilder;
import com.kasztelanic.carcare.service.dto.RefuelDto;
import com.kasztelanic.carcare.service.dto.RefuelDto.RefuelDtoBuilder;

@Service
public class RefuelMapper {

    @Autowired
    private VehicleEventMapper vehicleEventMapper;

    public RefuelDto refuelToRefuelDto(Refuel refuel) {
        RefuelDtoBuilder builder = RefuelDto.builder();
        builder.uuid(refuel.getUuid());
        builder.costInCents(refuel.getCostInCents());
        builder.station(refuel.getStation());
        builder.volume(refuel.getVolume());
        builder.vehicleEvent(vehicleEventMapper.vehicleEventToVehicleEventDto(refuel.getVehicleEvent()));
        return builder.build();
    }

    public Refuel refuelDtoToRefuel(RefuelDto refuel) {
        RefuelBuilder builder = Refuel.builder();
        builder.costInCents(refuel.getCostInCents());
        builder.station(refuel.getStation());
        builder.volume(refuel.getVolume());
        builder.vehicleEvent(vehicleEventMapper.vehicleEventDtoToVehicleEvent(refuel.getVehicleEvent()));
        return builder.build();
    }
}
