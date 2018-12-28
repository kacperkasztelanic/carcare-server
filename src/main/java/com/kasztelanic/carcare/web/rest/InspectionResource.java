package com.kasztelanic.carcare.web.rest;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kasztelanic.carcare.domain.Inspection;
import com.kasztelanic.carcare.repository.InspectionRepository;
import com.kasztelanic.carcare.repository.VehicleRepository;
import com.kasztelanic.carcare.service.dto.InspectionDto;
import com.kasztelanic.carcare.service.mapper.InspectionMapper;
import com.kasztelanic.carcare.web.rest.util.HeaderUtil;
import com.kasztelanic.carcare.web.rest.util.ResponseUtil;
import com.kasztelanic.carcare.web.rest.util.URIUtil;

@RestController
@RequestMapping("/api/inspection")
public class InspectionResource {

    private static final String ENTITY_NAME = "inspection";

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private InspectionRepository inspectionRepository;

    @Autowired
    private InspectionMapper inspectionMapper;

    @Transactional
    @PostMapping("/{vehicleId}")
    public ResponseEntity<InspectionDto> addInspection(@PathVariable Long vehicleId,
            @RequestBody InspectionDto inspectionDto) {
        Inspection inspection = inspectionMapper.inspectionDtoToInspection(inspectionDto);
        return vehicleRepository.findByIdAndOwnerIsCurrentUser(vehicleId).map(inspection::setVehicle)
                .map(inspectionRepository::save).map(inspectionMapper::inspectionToInspectionDto)
                .map(i -> ResponseEntity
                        .created(URIUtil.buildURI(
                                String.format("/api/inspection/%s/%s", vehicleId.toString(), i.getId().toString())))
                        .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, i.getId().toString())).body(i))
                .orElse(ResponseEntity.notFound().build());
    }

    @Transactional
    @PutMapping("/{inspectionId}")
    public ResponseEntity<InspectionDto> editInspection(@PathVariable Long inspectionId,
            @RequestBody InspectionDto inspectionDto) {
        return inspectionRepository.findByIdAndOwnerIsCurrentUser(inspectionId)
                .map(i -> updateInspection(i, inspectionMapper.inspectionDtoToInspection(inspectionDto)))
                .map(inspectionRepository::save).map(inspectionMapper::inspectionToInspectionDto)
                .map(i -> ResponseEntity.ok()
                        .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, i.getId().toString())).body(i))
                .orElse(ResponseEntity.notFound().build());
    }

    @Transactional
    @DeleteMapping("/{inspectionId}")
    public ResponseEntity<Void> deleteInspection(@PathVariable Long inspectionId) {
        Optional<Inspection> inspection = inspectionRepository.findByIdAndOwnerIsCurrentUser(inspectionId);
        if (inspection.isPresent()) {
            inspectionRepository.delete(inspection.get());
            return ResponseEntity.ok()
                    .headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, inspectionId.toString())).build();
        }
        return ResponseEntity.notFound().build();
    }

    @Transactional
    @GetMapping("/all/{vehicleId}")
    public ResponseEntity<List<InspectionDto>> getAllInspections(@PathVariable Long vehicleId) {
        List<InspectionDto> list = inspectionRepository.findByVehicleIdAndOwnerIsCurrentUser(vehicleId).stream()
                .map(inspectionMapper::inspectionToInspectionDto).collect(Collectors.toList());
        return ResponseUtil.createListOkResponse(list);
    }

    @Transactional
    @GetMapping("/{inspectionId}")
    public ResponseEntity<InspectionDto> getInspection(@PathVariable Long inspectionId) {
        return inspectionRepository.findByIdAndOwnerIsCurrentUser(inspectionId)
                .map(inspectionMapper::inspectionToInspectionDto).map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
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
