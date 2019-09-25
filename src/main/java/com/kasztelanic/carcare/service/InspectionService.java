package com.kasztelanic.carcare.service;

import com.kasztelanic.carcare.service.dto.InspectionDto;

import java.util.List;
import java.util.Optional;

public interface InspectionService {

    Optional<InspectionDto> getInspection(Long id);

    List<InspectionDto> getAllInspections(Long vehicleId);

    Optional<InspectionDto> addInspection(Long vehicleId, InspectionDto inspectionDto);

    Optional<InspectionDto> editInspection(Long id, InspectionDto inspectionDto);

    Optional<InspectionDto> deleteInspection(Long id);
}
