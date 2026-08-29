package com.kasztelanic.carcare.service.impl;

import com.kasztelanic.carcare.domain.Refuel;
import com.kasztelanic.carcare.repository.RefuelRepository;
import com.kasztelanic.carcare.service.RefuelService;
import com.kasztelanic.carcare.service.VehicleScopeService;
import com.kasztelanic.carcare.service.dto.RefuelDto;
import com.kasztelanic.carcare.service.mapper.RefuelMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RefuelServiceImpl implements RefuelService {

    private final VehicleScopeService vehicleScopeService;
    private final RefuelRepository refuelRepository;
    private final RefuelMapper refuelMapper;

    @Override
    @Transactional(readOnly = true)
    public Optional<RefuelDto> getRefuel(Long id) {
        return refuelRepository.findByIdAndOwnerIsCurrentUser(id)//
            .map(refuel -> {
                vehicleScopeService.assertActiveVehicle(refuel.getVehicle());
                return refuelMapper.refuelToRefuelDto(refuel);
            });
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefuelDto> getAllRefuels(Long vehicleId) {
        return vehicleScopeService.findActiveOwnedVehicle(vehicleId)
            .map(vehicle -> refuelRepository.findByVehicleIdAndOwnerIsCurrentUser(vehicleId).stream()
                .map(refuelMapper::refuelToRefuelDto)
                .collect(Collectors.toList()))
            .orElseGet(List::of);
    }

    @Override
    @Transactional
    public Optional<RefuelDto> addRefuel(Long vehicleId, RefuelDto refuelDto) {
        return vehicleScopeService.findActiveOwnedVehicle(vehicleId)//
            .map(v -> refuelMapper.refuelDtoToRefuel(refuelDto).setVehicle(v))//
            .map(refuelRepository::save)//
            .map(refuelMapper::refuelToRefuelDto);
    }

    @Override
    @Transactional
    public Optional<RefuelDto> editRefuel(Long id, RefuelDto refuelDto) {
        return refuelRepository.findByIdAndOwnerIsCurrentUser(id)//
            .map(refuel -> {
                vehicleScopeService.assertActiveVehicle(refuel.getVehicle());
                return updateRefuel(refuel, refuelMapper.refuelDtoToRefuel(refuelDto));
            })//
            .map(refuelRepository::save)//
            .map(refuelMapper::refuelToRefuelDto);
    }

    @Override
    @Transactional
    public Optional<RefuelDto> deleteRefuel(Long id) {
        Optional<RefuelDto> refuel = refuelRepository.findByIdAndOwnerIsCurrentUser(id)
            .map(entity -> {
                vehicleScopeService.assertActiveVehicle(entity.getVehicle());
                return refuelMapper.refuelToRefuelDto(entity);
            });
        refuel.ifPresent(r -> refuelRepository.deleteById(id));
        return refuel;
    }

    private static Refuel updateRefuel(Refuel refuel, Refuel updatedRefuel) {
        refuel.setCostInCents(updatedRefuel.getCostInCents());
        refuel.setStation(updatedRefuel.getStation());
        refuel.setVolume(updatedRefuel.getVolume());
        refuel.setVehicleEvent(updatedRefuel.getVehicleEvent());
        return refuel;
    }
}
