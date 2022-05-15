package com.kasztelanic.carcare.service.impl;

import com.kasztelanic.carcare.domain.Repair;
import com.kasztelanic.carcare.repository.RepairRepository;
import com.kasztelanic.carcare.repository.VehicleRepository;
import com.kasztelanic.carcare.service.RepairService;
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

    private final VehicleRepository vehicleRepository;
    private final RepairRepository repairRepository;
    private final RepairMapper repairMapper;

    @Override
    @Transactional(readOnly = true)
    public Optional<RepairDto> getRepair(Long id) {
        return repairRepository.findByIdAndOwnerIsCurrentUser(id)//
            .map(repairMapper::repairToRepairDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RepairDto> getAllRepairs(Long vehicleId) {
        return repairRepository.findByVehicleIdAndOwnerIsCurrentUser(vehicleId).stream()//
            .map(repairMapper::repairToRepairDto)//
            .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public Optional<RepairDto> addRepair(Long vehicleId, RepairDto repairDto) {
        return vehicleRepository.findByIdAndOwnerIsCurrentUser(vehicleId)//
            .map(v -> repairMapper.repairDtoToRepair(repairDto).setVehicle(v))//
            .map(repairRepository::save)//
            .map(repairMapper::repairToRepairDto);
    }

    @Override
    @Transactional
    public Optional<RepairDto> editRepair(Long repairId, RepairDto repairDto) {
        return repairRepository.findByIdAndOwnerIsCurrentUser(repairId)//
            .map(i -> updateRepair(i, repairMapper.repairDtoToRepair(repairDto)))//
            .map(repairRepository::save)//
            .map(repairMapper::repairToRepairDto);
    }

    @Override
    @Transactional
    public Optional<RepairDto> deleteRepair(Long id) {
        Optional<RepairDto> repair = repairRepository.findByIdAndOwnerIsCurrentUser(id)//
            .map(repairMapper::repairToRepairDto);
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
