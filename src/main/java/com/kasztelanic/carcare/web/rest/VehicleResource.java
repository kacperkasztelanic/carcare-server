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

import com.codahale.metrics.annotation.Timed;
import com.kasztelanic.carcare.domain.Vehicle;
import com.kasztelanic.carcare.repository.VehicleRepository;
import com.kasztelanic.carcare.service.ImageStorageService;
import com.kasztelanic.carcare.service.UserService;
import com.kasztelanic.carcare.service.dto.VehicleDto;
import com.kasztelanic.carcare.service.mapper.VehicleMapper;
import com.kasztelanic.carcare.web.rest.util.HeaderUtil;
import com.kasztelanic.carcare.web.rest.util.ResponseUtil;
import com.kasztelanic.carcare.web.rest.util.URIUtil;

@RestController
@RequestMapping("/api/vehicle")
public class VehicleResource {

    private static final String ENTITY_NAME = "vehicle";

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private VehicleMapper vehicleMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private ImageStorageService imageStorageService;

    @Timed
    @Transactional
    @PostMapping("")
    public ResponseEntity<VehicleDto> addVehicle(@RequestBody VehicleDto vehicleDto) {
        Vehicle vehicle = vehicleMapper.vehicleDtoToVehicle(vehicleDto);
        vehicle.setOwner(userService.getUserWithAuthorities().orElseThrow(IllegalStateException::new));
        VehicleDto result = vehicleMapper.vehicleToVehicleDto(vehicleRepository.save(vehicle));
        return ResponseEntity.created(URIUtil.buildURI(String.format("/api/vehicle/%s", result.getId().toString())))
                .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString())).body(result);
    }

    @Timed
    @Transactional
    @PutMapping("/{id}")
    public ResponseEntity<VehicleDto> editVehicle(@PathVariable Long id, @RequestBody VehicleDto vehicleDto) {
        return vehicleRepository.findByIdAndOwnerIsCurrentUser(id)
                .map(i -> updateVehicle(i, vehicleMapper.vehicleDtoToVehicle(vehicleDto))).map(vehicleRepository::save)
                .map(vehicleMapper::vehicleToVehicleDto)
                .map(i -> ResponseEntity.ok()
                        .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, i.getId().toString())).body(i))
                .orElse(ResponseEntity.notFound().build());
    }

    @Timed
    @Transactional
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVehicle(@PathVariable Long id) {
        Optional<Vehicle> vehicle = vehicleRepository.findByIdAndOwnerIsCurrentUser(id);
        if (vehicle.isPresent()) {
            vehicleRepository.delete(vehicle.get());
            return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString()))
                    .build();
        }
        return ResponseEntity.notFound().build();
    }

    @Timed
    @Transactional
    @GetMapping("/all")
    public ResponseEntity<List<VehicleDto>> getAllVehicles() {
        List<VehicleDto> list = vehicleRepository.findByOwnerIsCurrentUser().stream()
                .map(vehicleMapper::vehicleToVehicleDto).collect(Collectors.toList());
        return ResponseUtil.createListOkResponse(list);
    }

    @Transactional
    @GetMapping("/{id}")
    public ResponseEntity<VehicleDto> getVehicle(@PathVariable Long id) {
        return vehicleRepository.findByIdAndOwnerIsCurrentUser(id).map(vehicleMapper::vehicleToVehicleDto)
                .map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    private Vehicle updateVehicle(Vehicle vehicle, Vehicle updatedVehicle) {
        imageStorageService.delete(vehicle.getVehicleDetails().getImage());
        vehicle.setFuelType(updatedVehicle.getFuelType());
        vehicle.setLicensePlate(updatedVehicle.getLicensePlate());
        vehicle.setMake(updatedVehicle.getMake());
        vehicle.setModel(vehicle.getModel());
        vehicle.setVehicleDetails(updatedVehicle.getVehicleDetails());
        return vehicle;
    }
}
