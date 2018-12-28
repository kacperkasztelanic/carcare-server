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

import com.kasztelanic.carcare.domain.Refuel;
import com.kasztelanic.carcare.repository.RefuelRepository;
import com.kasztelanic.carcare.repository.VehicleRepository;
import com.kasztelanic.carcare.service.dto.RefuelDto;
import com.kasztelanic.carcare.service.mapper.RefuelMapper;
import com.kasztelanic.carcare.web.rest.util.HeaderUtil;
import com.kasztelanic.carcare.web.rest.util.URIUtil;

@RestController
@RequestMapping("/api/refuel")
public class RefuelResource {

    private static final String ENTITY_NAME = "refuel";

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private RefuelRepository refuelRepository;

    @Autowired
    private RefuelMapper refuelMapper;

    @Transactional
    @PostMapping("/{vehicleId}")
    public ResponseEntity<RefuelDto> addRefuel(@PathVariable Long vehicleId, @RequestBody RefuelDto refuelDto) {
        System.err.println(refuelDto.getStation());
        Refuel refuel = refuelMapper.refuelDtoToRefuel(refuelDto);
        return vehicleRepository.findByIdAndOwnerIsCurrentUser(vehicleId).map(refuel::setVehicle)
                .map(refuelRepository::save).map(refuelMapper::refuelToRefuelDto)
                .map(i -> ResponseEntity
                        .created(URIUtil.buildURI(
                                String.format("/api/refuel/%s/%s", vehicleId.toString(), i.getId().toString())))
                        .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, i.getId().toString())).body(i))
                .orElse(ResponseEntity.notFound().build());
    }

    @Transactional
    @PutMapping("/{refuelId}")
    public ResponseEntity<RefuelDto> editRefuel(@PathVariable Long refuelId, @RequestBody RefuelDto refuelDto) {
        return refuelRepository.findByIdAndOwnerIsCurrentUser(refuelId)
                .map(i -> updateRefuel(i, refuelMapper.refuelDtoToRefuel(refuelDto))).map(refuelRepository::save)
                .map(refuelMapper::refuelToRefuelDto)
                .map(i -> ResponseEntity.ok()
                        .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, i.getId().toString())).body(i))
                .orElse(ResponseEntity.notFound().build());
    }

    @Transactional
    @DeleteMapping("/{refuelId}")
    public ResponseEntity<Void> deleteRefuel(@PathVariable Long refuelId) {
        Optional<Refuel> insurance = refuelRepository.findByIdAndOwnerIsCurrentUser(refuelId);
        if (insurance.isPresent()) {
            refuelRepository.delete(insurance.get());
            return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, refuelId.toString()))
                    .build();
        }
        return ResponseEntity.notFound().build();
    }

    @Transactional
    @GetMapping("/all/{vehicleId}")
    public ResponseEntity<List<RefuelDto>> getAllRefuels(@PathVariable Long vehicleId) {
        List<RefuelDto> list = refuelRepository.findByVehicleIdAndOwnerIsCurrentUser(vehicleId).stream()
                .map(refuelMapper::refuelToRefuelDto).collect(Collectors.toList());
        return ResponseEntity.ok().header("X-Total-Count", String.valueOf(list.size())).body(list);
    }

    @Transactional
    @GetMapping("/{refuelId}")
    public ResponseEntity<RefuelDto> getRefuel(@PathVariable Long refuelId) {
        return refuelRepository.findByIdAndOwnerIsCurrentUser(refuelId).map(refuelMapper::refuelToRefuelDto)
                .map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    private static Refuel updateRefuel(Refuel refuel, Refuel updatedRefuel) {
        refuel.setCostInCents(updatedRefuel.getCostInCents());
        refuel.setStation(updatedRefuel.getStation());
        refuel.setVolume(updatedRefuel.getVolume());
        refuel.setVehicleEvent(updatedRefuel.getVehicleEvent());
        return refuel;
    }
}
