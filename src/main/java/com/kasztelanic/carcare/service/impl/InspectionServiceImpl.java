package com.kasztelanic.carcare.service.impl;

import com.kasztelanic.carcare.domain.Inspection;
import com.kasztelanic.carcare.repository.InspectionRepository;
import com.kasztelanic.carcare.repository.VehicleRepository;
import com.kasztelanic.carcare.service.InspectionService;
import com.kasztelanic.carcare.service.dto.InspectionDto;
import com.kasztelanic.carcare.service.mapper.InspectionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InspectionServiceImpl implements InspectionService {

    private final VehicleRepository vehicleRepository;
    private final InspectionRepository inspectionRepository;
    private final InspectionMapper inspectionMapper;

    @Override
    @Transactional(readOnly = true)
    public Optional<InspectionDto> getInspection(Long id) {
        return inspectionRepository.findByIdAndOwnerIsCurrentUser(id)//
            .map(inspectionMapper::inspectionToInspectionDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InspectionDto> getAllInspections(Long vehicleId) {
        return inspectionRepository.findByVehicleIdAndOwnerIsCurrentUser(vehicleId).stream()//
            .map(inspectionMapper::inspectionToInspectionDto)//
            .collect(Collectors.toList());
    }


    @Override
    @Transactional
    public Optional<InspectionDto> addInspection(Long vehicleId, InspectionDto inspectionDto) {
        return vehicleRepository.findByIdAndOwnerIsCurrentUser(vehicleId)//
            .map(v -> inspectionMapper.inspectionDtoToInspection(inspectionDto).setVehicle(v))//
            .map(inspectionRepository::save)//
            .map(inspectionMapper::inspectionToInspectionDto);
    }

    @Override
    @Transactional
    public Optional<InspectionDto> editInspection(Long id, InspectionDto inspectionDto) {
        return inspectionRepository.findByIdAndOwnerIsCurrentUser(id)//
            .map(i -> updateInspection(i, inspectionMapper.inspectionDtoToInspection(inspectionDto)))//
            .map(inspectionRepository::save)//
            .map(inspectionMapper::inspectionToInspectionDto);
    }

    @Override
    @Transactional
    public Optional<InspectionDto> deleteInspection(Long id) {
        Optional<InspectionDto> inspection = inspectionRepository.findByIdAndOwnerIsCurrentUser(id)//
            .map(inspectionMapper::inspectionToInspectionDto);
        inspection.ifPresent(i -> inspectionRepository.deleteById(id));
        return inspection;
    }

    private static Inspection updateInspection(Inspection inspection, Inspection updatedInspection) {
        inspection.setCostInCents(updatedInspection.getCostInCents());
        inspection.setDetails(updatedInspection.getDetails());
        inspection.setStation(updatedInspection.getStation());
        inspection.setValidThru(updatedInspection.getValidThru());
        inspection.setVehicleEvent(updatedInspection.getVehicleEvent());
        return inspection;
    }
}
