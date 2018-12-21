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

import com.kasztelanic.carcare.domain.Repair;
import com.kasztelanic.carcare.repository.RepairRepository;
import com.kasztelanic.carcare.repository.VehicleRepository;
import com.kasztelanic.carcare.service.dto.RepairDto;
import com.kasztelanic.carcare.service.mapper.RepairMapper;
import com.kasztelanic.carcare.web.rest.util.HeaderUtil;
import com.kasztelanic.carcare.web.rest.util.URIUtil;

@RestController
@RequestMapping("/api/repair")
public class RepairResource {

    private static final String ENTITY_NAME = "repair";

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private RepairRepository repairRepository;

    @Autowired
    private RepairMapper repairMapper;

    @Transactional
    @PostMapping("/{vehicleId}")
    public ResponseEntity<RepairDto> addRepair(@PathVariable Long vehicleId, @RequestBody RepairDto repairDto) {
        Repair repair = repairMapper.repairDtoToRepair(repairDto);
        return vehicleRepository.findByIdAndOwnerIsCurrentUser(vehicleId).map(repair::setVehicle)
                .map(repairRepository::save).map(repairMapper::repairToRepairDto)
                .map(i -> ResponseEntity
                        .created(URIUtil.buildURI(
                                String.format("/api/repair/%s/%s", vehicleId.toString(), i.getId().toString())))
                        .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, i.getId().toString())).body(i))
                .orElse(ResponseEntity.notFound().build());
    }

    @Transactional
    @PutMapping("/{repairId}")
    public ResponseEntity<RepairDto> editRepair(@PathVariable Long repairId, @RequestBody RepairDto repairDto) {
        return repairRepository.findByIdAndOwnerIsCurrentUser(repairId)
                .map(i -> updateRepair(i, repairMapper.repairDtoToRepair(repairDto))).map(repairRepository::save)
                .map(repairMapper::repairToRepairDto)
                .map(i -> ResponseEntity.ok()
                        .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, i.getId().toString())).body(i))
                .orElse(ResponseEntity.notFound().build());
    }

    @Transactional
    @DeleteMapping("/{repairId}")
    public ResponseEntity<Void> deleteRepair(@PathVariable Long repairId) {
        Optional<Repair> repair = repairRepository.findByIdAndOwnerIsCurrentUser(repairId);
        if (repair.isPresent()) {
            repairRepository.delete(repair.get());
            return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, repairId.toString()))
                    .build();
        }
        return ResponseEntity.notFound().build();
    }

    @Transactional
    @GetMapping("/all/{vehicleId}")
    public List<RepairDto> getAllRepairs(@PathVariable Long vehicleId) {
        return repairRepository.findByVehicleIdAndOwnerIsCurrentUser(vehicleId).stream()
                .map(repairMapper::repairToRepairDto).collect(Collectors.toList());
    }

    @Transactional
    @GetMapping("/{repairId}")
    public ResponseEntity<RepairDto> getRepair(@PathVariable Long repairId) {
        return repairRepository.findByIdAndOwnerIsCurrentUser(repairId).map(repairMapper::repairToRepairDto)
                .map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    private static Repair updateRepair(Repair repair, Repair updatedRepair) {
        repair.setCostInCents(updatedRepair.getCostInCents());
        repair.setDetails(updatedRepair.getDetails());
        repair.setStation(updatedRepair.getStation());
        repair.setVehicleEvent(updatedRepair.getVehicleEvent());
        return repair;
    }
}
