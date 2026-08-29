package com.kasztelanic.carcare.service.impl;

import com.kasztelanic.carcare.domain.RoutineService;
import com.kasztelanic.carcare.repository.RoutineServiceRepository;
import com.kasztelanic.carcare.service.RoutineServiceService;
import com.kasztelanic.carcare.service.VehicleScopeService;
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

    private final VehicleScopeService vehicleScopeService;
    private final RoutineServiceRepository routineServiceRepository;
    private final RoutineServiceMapper routineServiceMapper;

    @Override
    @Transactional(readOnly = true)
    public Optional<RoutineServiceDto> getRoutineService(Long id) {
        return routineServiceRepository.findByIdAndOwnerIsCurrentUser(id)//
            .map(routineService -> {
                vehicleScopeService.assertActiveVehicle(routineService.getVehicle());
                return routineServiceMapper.routineServiceToRoutineServiceDto(routineService);
            });
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoutineServiceDto> getAllRoutineServices(Long vehicleId) {
        return vehicleScopeService.findActiveOwnedVehicle(vehicleId)
            .map(vehicle -> routineServiceRepository.findByVehicleIdAndOwnerIsCurrentUser(vehicleId).stream()
                .map(routineServiceMapper::routineServiceToRoutineServiceDto)
                .collect(Collectors.toList()))
            .orElseGet(List::of);
    }

    @Override
    @Transactional
    public Optional<RoutineServiceDto> addRoutineService(Long vehicleId, RoutineServiceDto routineServiceDto) {
        return vehicleScopeService.findActiveOwnedVehicle(vehicleId)//
            .map(v -> routineServiceMapper.routineServiceDtoToRoutineService(routineServiceDto).setVehicle(v))//
            .map(routineServiceRepository::save)//
            .map(routineServiceMapper::routineServiceToRoutineServiceDto);
    }

    @Override
    @Transactional
    public Optional<RoutineServiceDto> editRoutineService(Long id, RoutineServiceDto routineServiceDto) {
        return routineServiceRepository.findByIdAndOwnerIsCurrentUser(id)//
            .map(routineService -> {
                vehicleScopeService.assertActiveVehicle(routineService.getVehicle());
                return updateRoutineService(routineService,
                    routineServiceMapper.routineServiceDtoToRoutineService(routineServiceDto));
            })//
            .map(routineServiceRepository::save).map(routineServiceMapper::routineServiceToRoutineServiceDto);
    }

    @Override
    @Transactional
    public Optional<RoutineServiceDto> deleteRoutineService(Long id) {
        Optional<RoutineServiceDto> routineService = routineServiceRepository.findByIdAndOwnerIsCurrentUser(id)
            .map(entity -> {
                vehicleScopeService.assertActiveVehicle(entity.getVehicle());
                return routineServiceMapper.routineServiceToRoutineServiceDto(entity);
            });
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
