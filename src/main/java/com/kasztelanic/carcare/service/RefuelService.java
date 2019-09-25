package com.kasztelanic.carcare.service;

import com.kasztelanic.carcare.service.dto.RefuelDto;

import java.util.List;
import java.util.Optional;

public interface RefuelService {

    Optional<RefuelDto> getRefuel(Long id);

    List<RefuelDto> getAllRefuels(Long vehicleId);

    Optional<RefuelDto> addRefuel(Long vehicleId, RefuelDto refuelDto);

    Optional<RefuelDto> editRefuel(Long id, RefuelDto refuelDto);

    Optional<RefuelDto> deleteRefuel(Long id);
}
