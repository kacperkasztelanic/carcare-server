package com.kasztelanic.carcare.service.mapper;

import com.kasztelanic.carcare.domain.Repair;
import com.kasztelanic.carcare.service.dto.RepairDto;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RepairMapper {

    private final VehicleEventMapper vehicleEventMapper;

    @Autowired
    public RepairMapper(VehicleEventMapper vehicleEventMapper) {
        this.vehicleEventMapper = vehicleEventMapper;
    }

    public RepairDto repairToRepairDto(Repair repair) {
        return RepairDto.builder()//
                .id(repair.getId())//
                .costInCents(repair.getCostInCents())//
                .details(repair.getDetails())//
                .station(repair.getStation())//
                .vehicleEvent(vehicleEventMapper.vehicleEventToVehicleEventDto(repair.getVehicleEvent()))//
                .vehicleId(repair.getVehicle().getId())//
                .build();
    }

    public Repair repairDtoToRepair(RepairDto repairDto) {
        return Repair.builder()//
                .costInCents(repairDto.getCostInCents())//
                .details(repairDto.getDetails().trim())//
                .station(repairDto.getStation().trim())//
                .vehicleEvent(vehicleEventMapper.vehicleEventDtoToVehicleEvent(repairDto.getVehicleEvent()))//
                .build();
    }
}
