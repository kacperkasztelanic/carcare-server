package com.kasztelanic.carcare.service.impl;

import com.kasztelanic.carcare.domain.RoutineService;
import com.kasztelanic.carcare.repository.RoutineServiceRepository;
import com.kasztelanic.carcare.repository.VehicleRepository;
import com.kasztelanic.carcare.service.RoutineServiceService;
import com.kasztelanic.carcare.service.dto.RoutineServiceDto;
import com.kasztelanic.carcare.service.mapper.RoutineServiceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoutineServiceServiceImpl implements RoutineServiceService {

    private final VehicleRepository vehicleRepository;
    private final RoutineServiceRepository routineServiceRepository;
    private final RoutineServiceMapper routineServiceMapper;

    @Override
    @Transactional(readOnly = true)
    public Optional<RoutineServiceDto> getRoutineService(Long id) {
        return routineServiceRepository.findByIdAndOwnerIsCurrentUser(id)//
            .map(routineServiceMapper::routineServiceToRoutineServiceDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoutineServiceDto> getAllRoutineServices(Long vehicleId) {
        return routineServiceRepository.findByVehicleIdAndOwnerIsCurrentUser(vehicleId).stream()//
            .map(routineServiceMapper::routineServiceToRoutineServiceDto)//
            .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public Optional<RoutineServiceDto> addRoutineService(Long vehicleId, RoutineServiceDto routineServiceDto) {
        return vehicleRepository.findByIdAndOwnerIsCurrentUser(vehicleId)//
            .map(v -> routineServiceMapper.routineServiceDtoToRoutineService(routineServiceDto).setVehicle(v))//
            .map(routineServiceRepository::save)//
            .map(routineServiceMapper::routineServiceToRoutineServiceDto);
    }

    @Override
    @Transactional
    public Optional<RoutineServiceDto> editRoutineService(Long id, RoutineServiceDto routineServiceDto) {
        return routineServiceRepository.findByIdAndOwnerIsCurrentUser(id)//
            .map(i -> updateRoutineService(i,
                routineServiceMapper.routineServiceDtoToRoutineService(routineServiceDto)))//
            .map(routineServiceRepository::save).map(routineServiceMapper::routineServiceToRoutineServiceDto);
    }

    @Override
    @Transactional
    public Optional<RoutineServiceDto> deleteRoutineService(Long id) {
        Optional<RoutineServiceDto> routineService = routineServiceRepository.findByIdAndOwnerIsCurrentUser(id)//
            .map(routineServiceMapper::routineServiceToRoutineServiceDto);
        routineService.ifPresent(r -> routineServiceRepository.deleteById(id));
        return routineService;
    }

    private static RoutineService updateRoutineService(RoutineService routineService,
                                                       RoutineService updatedRoutineService) {
        routineService.setCostInCents(updatedRoutineService.getCostInCents());
        routineService.setDetails(updatedRoutineService.getDetails());
        routineService.setNextByDate(updatedRoutineService.getNextByDate());
        routineService.setNextByMileage(updatedRoutineService.getNextByMileage());
        routineService.setStation(updatedRoutineService.getStation());
        routineService.setVehicleEvent(updatedRoutineService.getVehicleEvent());
        return routineService;
    }
}
