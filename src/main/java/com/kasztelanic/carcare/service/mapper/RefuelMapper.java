package com.kasztelanic.carcare.service.mapper;

import com.kasztelanic.carcare.domain.Refuel;
import com.kasztelanic.carcare.service.dto.RefuelDto;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RefuelMapper {

    private final VehicleEventMapper vehicleEventMapper;

    @Autowired
    public RefuelMapper(VehicleEventMapper vehicleEventMapper) {
        this.vehicleEventMapper = vehicleEventMapper;
    }

    public RefuelDto refuelToRefuelDto(Refuel refuel) {
        return RefuelDto.builder()//
                .id(refuel.getId())//
                .costInCents(refuel.getCostInCents())//
                .station(refuel.getStation())//
                .volume(refuel.getVolume())//
                .vehicleEvent(vehicleEventMapper.vehicleEventToVehicleEventDto(refuel.getVehicleEvent()))//
                .vehicleId(refuel.getVehicle().getId())//
                .build();
    }

    public Refuel refuelDtoToRefuel(RefuelDto refuelDto) {
        return Refuel.builder()//
                .costInCents(refuelDto.getCostInCents())//
                .station(refuelDto.getStation().trim())//
                .volume(refuelDto.getVolume())//
                .vehicleEvent(vehicleEventMapper.vehicleEventDtoToVehicleEvent(refuelDto.getVehicleEvent()))//
                .build();
    }
}
