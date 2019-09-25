package com.kasztelanic.carcare.service;

import com.kasztelanic.carcare.service.dto.RepairDto;

import java.util.List;
import java.util.Optional;

public interface RepairService {

    Optional<RepairDto> getRepair(Long id);

    List<RepairDto> getAllRepairs(Long vehicleId);

    Optional<RepairDto> addRepair(Long vehicleId, RepairDto repairDto);

    Optional<RepairDto> editRepair(Long repairId, RepairDto repairDto);

    Optional<RepairDto> deleteRepair(Long id);
}
