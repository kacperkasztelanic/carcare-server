package com.kasztelanic.carcare.service.mapper;

import org.springframework.stereotype.Service;

import com.kasztelanic.carcare.domain.Vehicle;
import com.kasztelanic.carcare.service.dto.VehicleRichDto;
import com.kasztelanic.carcare.service.dto.VehicleRichDto.VehicleRichDtoBuilder;

@Service
public class VehicleRichMapper {

    public VehicleRichDto vehicleToVehicleDto(Vehicle vehicle) {
        //TODO implement mapper
        VehicleRichDtoBuilder builder = VehicleRichDto.richBuilder();
        return builder.build();
    }
}
