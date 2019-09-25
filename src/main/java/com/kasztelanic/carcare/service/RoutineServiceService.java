package com.kasztelanic.carcare.service;

import com.kasztelanic.carcare.service.dto.RoutineServiceDto;

import java.util.List;
import java.util.Optional;

public interface RoutineServiceService {

    Optional<RoutineServiceDto> getRoutineService(Long id);

    List<RoutineServiceDto> getAllRoutineServices(Long vehicleId);

    Optional<RoutineServiceDto> addRoutineService(Long vehicleId, RoutineServiceDto routineServiceDto);

    Optional<RoutineServiceDto> editRoutineService(Long id, RoutineServiceDto routineServiceDto);

    Optional<RoutineServiceDto> deleteRoutineService(Long id);
}
