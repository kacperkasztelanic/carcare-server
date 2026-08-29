package com.kasztelanic.carcare.service.impl;

import com.kasztelanic.carcare.domain.Repair;
import com.kasztelanic.carcare.repository.RepairRepository;
import com.kasztelanic.carcare.service.RepairService;
import com.kasztelanic.carcare.service.VehicleScopeService;
import com.kasztelanic.carcare.service.dto.RepairDto;
import com.kasztelanic.carcare.service.mapper.RepairMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RepairServiceImpl implements RepairService {

    private final VehicleScopeService vehicleScopeService;
    private final RepairRepository repairRepository;
    private final RepairMapper repairMapper;

    @Override
    @Transactional(readOnly = true)
    public Optional<RepairDto> getRepair(Long id) {
        return repairRepository.findByIdAndOwnerIsCurrentUser(id)//
            .map(repair -> {
                vehicleScopeService.assertActiveVehicle(repair.getVehicle());
                return repairMapper.repairToRepairDto(repair);
            });
    }

    @Override
    @Transactional(readOnly = true)
    public List<RepairDto> getAllRepairs(Long vehicleId) {
        return vehicleScopeService.findActiveOwnedVehicle(vehicleId)
            .map(vehicle -> repairRepository.findByVehicleIdAndOwnerIsCurrentUser(vehicleId).stream()
                .map(repairMapper::repairToRepairDto)
                .collect(Collectors.toList()))
            .orElseGet(List::of);
    }

    @Override
    @Transactional
    public Optional<RepairDto> addRepair(Long vehicleId, RepairDto repairDto) {
        return vehicleScopeService.findActiveOwnedVehicle(vehicleId)//
            .map(v -> repairMapper.repairDtoToRepair(repairDto).setVehicle(v))//
            .map(repairRepository::save)//
            .map(repairMapper::repairToRepairDto);
    }

    @Override
    @Transactional
    public Optional<RepairDto> editRepair(Long repairId, RepairDto repairDto) {
        return repairRepository.findByIdAndOwnerIsCurrentUser(repairId)//
            .map(repair -> {
                vehicleScopeService.assertActiveVehicle(repair.getVehicle());
                return updateRepair(repair, repairMapper.repairDtoToRepair(repairDto));
            })//
            .map(repairRepository::save)//
            .map(repairMapper::repairToRepairDto);
    }

    @Override
    @Transactional
    public Optional<RepairDto> deleteRepair(Long id) {
        Optional<RepairDto> repair = repairRepository.findByIdAndOwnerIsCurrentUser(id)
            .map(entity -> {
                vehicleScopeService.assertActiveVehicle(entity.getVehicle());
                return repairMapper.repairToRepairDto(entity);
            });
        repair.ifPresent(r -> repairRepository.deleteById(id));
        return repair;
    }

    private static Repair updateRepair(Repair repair, Repair updatedRepair) {
        repair.setCostInCents(updatedRepair.getCostInCents());
        repair.setDetails(updatedRepair.getDetails());
        repair.setStation(updatedRepair.getStation());
        repair.setVehicleEvent(updatedRepair.getVehicleEvent());
        return repair;
    }
}
