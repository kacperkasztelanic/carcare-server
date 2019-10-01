package com.kasztelanic.carcare.service.mapper;

import com.kasztelanic.carcare.domain.Inspection;
import com.kasztelanic.carcare.service.dto.InspectionDto;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class InspectionMapper {

    private final VehicleEventMapper vehicleEventMapper;

    @Autowired
    public InspectionMapper(VehicleEventMapper vehicleEventMapper) {
        this.vehicleEventMapper = vehicleEventMapper;
    }

    public InspectionDto inspectionToInspectionDto(Inspection inspection) {
        return InspectionDto.builder()//
                .id(inspection.getId())//
                .costInCents(inspection.getCostInCents())//
                .details(inspection.getDetails())//
                .station(inspection.getStation())//
                .validThru(inspection.getValidThru())//
                .vehicleEvent(vehicleEventMapper.vehicleEventToVehicleEventDto(inspection.getVehicleEvent()))//
                .vehicleId(inspection.getVehicle().getId())//
                .build();
    }

    public Inspection inspectionDtoToInspection(InspectionDto inspectionDto) {
        return Inspection.builder()//
                .costInCents(inspectionDto.getCostInCents())//
                .details(inspectionDto.getDetails().trim())//
                .station(inspectionDto.getStation().trim())//
                .validThru(inspectionDto.getValidThru())//
                .vehicleEvent(vehicleEventMapper.vehicleEventDtoToVehicleEvent(inspectionDto.getVehicleEvent()))//
                .build();
    }
}
