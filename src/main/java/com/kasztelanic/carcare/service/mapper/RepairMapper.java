package com.kasztelanic.carcare.service.mapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.kasztelanic.carcare.domain.Repair;
import com.kasztelanic.carcare.domain.Repair.RepairBuilder;
import com.kasztelanic.carcare.service.dto.RepairDto;
import com.kasztelanic.carcare.service.dto.RepairDto.RepairDtoBuilder;

@Service
public class RepairMapper {

    @Autowired
    private VehicleEventMapper vehicleEventMapper;

    public RepairDto repairToRepairDto(Repair repair) {
        RepairDtoBuilder builder = RepairDto.builder();
        builder.id(repair.getId());
        builder.costInCents(repair.getCostInCents());
        builder.details(repair.getDetails());
        builder.station(repair.getStation());
        builder.vehicleEvent(vehicleEventMapper.vehicleEventToVehicleEventDto(repair.getVehicleEvent()));
        builder.vehicleId(repair.getVehicle().getId());
        return builder.build();
    }

    public Repair repairDtoToRepair(RepairDto repairDto) {
        RepairBuilder builder = Repair.builder();
        builder.costInCents(repairDto.getCostInCents());
        builder.details(repairDto.getDetails().trim());
        builder.station(repairDto.getStation().trim());
        builder.vehicleEvent(vehicleEventMapper.vehicleEventDtoToVehicleEvent(repairDto.getVehicleEvent()));
        return builder.build();
    }
}
