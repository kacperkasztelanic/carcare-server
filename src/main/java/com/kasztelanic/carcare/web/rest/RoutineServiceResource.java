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

import com.kasztelanic.carcare.domain.RoutineService;
import com.kasztelanic.carcare.repository.RoutineServiceRepository;
import com.kasztelanic.carcare.repository.VehicleRepository;
import com.kasztelanic.carcare.service.dto.RoutineServiceDto;
import com.kasztelanic.carcare.service.mapper.RoutineServiceMapper;
import com.kasztelanic.carcare.web.rest.util.HeaderUtil;
import com.kasztelanic.carcare.web.rest.util.URIUtil;

@RestController
@RequestMapping("/api/routine-service")
public class RoutineServiceResource {

    private static final String ENTITY_NAME = "routineService";

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private RoutineServiceRepository routineServiceRepository;

    @Autowired
    private RoutineServiceMapper routineServiceMapper;

    @Transactional
    @PutMapping("/{vehicleId}")
    public ResponseEntity<RoutineServiceDto> addRoutineService(@PathVariable Long vehicleId,
            @RequestBody RoutineServiceDto routineServiceDto) {
        RoutineService routineService = routineServiceMapper.routineServiceDtoToRoutineService(routineServiceDto);
        return vehicleRepository.findByIdAndOwnerIsCurrentUser(vehicleId).map(routineService::setVehicle)
                .map(routineServiceRepository::save).map(routineServiceMapper::routineServiceToRoutineServiceDto)
                .map(i -> ResponseEntity
                        .created(URIUtil.buildURI(
                                String.format("/api/routine-service/%s/%s", vehicleId.toString(), i.getId().toString())))
                        .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, i.getId().toString())).body(i))
                .orElse(ResponseEntity.notFound().build());
    }

    @Transactional
    @PostMapping("/{routineServiceId}")
    public ResponseEntity<RoutineServiceDto> editRoutineService(@PathVariable Long routineServiceId,
            @RequestBody RoutineServiceDto routineServiceDto) {
        return routineServiceRepository.findByIdAndOwnerIsCurrentUser(routineServiceId)
                .map(i -> updateRoutineService(i,
                        routineServiceMapper.routineServiceDtoToRoutineService(routineServiceDto)))
                .map(routineServiceRepository::save).map(routineServiceMapper::routineServiceToRoutineServiceDto)
                .map(i -> ResponseEntity.ok()
                        .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, i.getId().toString())).body(i))
                .orElse(ResponseEntity.notFound().build());
    }

    @Transactional
    @DeleteMapping("/{routineServiceId}")
    public ResponseEntity<Void> deleteRoutineService(@PathVariable Long routineServiceId) {
        Optional<RoutineService> routineService = routineServiceRepository
                .findByIdAndOwnerIsCurrentUser(routineServiceId);
        if (routineService.isPresent()) {
            routineServiceRepository.delete(routineService.get());
            return ResponseEntity.ok()
                    .headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, routineServiceId.toString())).build();
        }
        return ResponseEntity.notFound().build();
    }

    @Transactional
    @GetMapping("/all/{vehicleId}")
    public List<RoutineServiceDto> getAllRoutineServices(@PathVariable Long vehicleId) {
        return routineServiceRepository.findByVehicleIdAndOwnerIsCurrentUser(vehicleId).stream()
                .map(routineServiceMapper::routineServiceToRoutineServiceDto).collect(Collectors.toList());
    }

    @Transactional
    @GetMapping("/{routineServiceId}")
    public ResponseEntity<RoutineServiceDto> getRoutineService(@PathVariable Long routineServiceId) {
        return routineServiceRepository.findByIdAndOwnerIsCurrentUser(routineServiceId)
                .map(routineServiceMapper::routineServiceToRoutineServiceDto).map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private static RoutineService updateRoutineService(RoutineService routineService,
            RoutineService updatedRoutineService) {
        routineService.setCostInCents(updatedRoutineService.getCostInCents());
        routineService.setDetails(updatedRoutineService.getDetails());
        routineService.setNextByDate(updatedRoutineService.getNextByDate());
        routineService.setNextByMileage(updatedRoutineService.getNextByMileage());
        routineService.setStation(updatedRoutineService.getStation());
        routineService.setVehicleEvent(updatedRoutineService.getVehicleEvent());
        return routineService;
    }
}
