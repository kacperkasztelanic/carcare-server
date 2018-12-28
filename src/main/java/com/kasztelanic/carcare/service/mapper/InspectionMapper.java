package com.kasztelanic.carcare.service.mapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.kasztelanic.carcare.domain.Inspection;
import com.kasztelanic.carcare.domain.Inspection.InspectionBuilder;
import com.kasztelanic.carcare.service.dto.InspectionDto;
import com.kasztelanic.carcare.service.dto.InspectionDto.InspectionDtoBuilder;

@Service
public class InspectionMapper {

    @Autowired
    private VehicleEventMapper vehicleEventMapper;

    public InspectionDto inspectionToInspectionDto(Inspection inspection) {
        InspectionDtoBuilder builder = InspectionDto.builder();
        builder.id(inspection.getId());
        builder.costInCents(inspection.getCostInCents());
        builder.details(inspection.getDetails());
        builder.station(inspection.getStation());
        builder.validThru(inspection.getValidThru());
        builder.vehicleEvent(vehicleEventMapper.vehicleEventToVehicleEventDto(inspection.getVehicleEvent()));
        builder.vehicleId(inspection.getVehicle().getId());
        return builder.build();
    }

    public Inspection inspectionDtoToInspection(InspectionDto inspectionDto) {
        InspectionBuilder builder = Inspection.builder();
        builder.costInCents(inspectionDto.getCostInCents());
        builder.details(inspectionDto.getDetails());
        builder.station(inspectionDto.getStation());
        builder.validThru(inspectionDto.getValidThru());
        builder.vehicleEvent(vehicleEventMapper.vehicleEventDtoToVehicleEvent(inspectionDto.getVehicleEvent()));
        return builder.build();
    }
}
