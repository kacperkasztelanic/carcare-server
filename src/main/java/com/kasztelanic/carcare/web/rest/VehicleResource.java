package com.kasztelanic.carcare.web.rest;

import com.kasztelanic.carcare.service.UserService;
import com.kasztelanic.carcare.service.VehicleService;
import com.kasztelanic.carcare.service.dto.VehicleDto;
import com.kasztelanic.carcare.web.rest.util.HeaderUtil;
import com.kasztelanic.carcare.web.rest.util.ResponseUtil;
import com.kasztelanic.carcare.web.rest.util.URIUtil;

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

import java.util.List;

@RestController
@RequestMapping("/api/vehicle")
public class VehicleResource {

    private static final String ENTITY_NAME = "vehicle";

    private final VehicleService vehicleService;
    private final UserService userService;

    @Autowired
    public VehicleResource(VehicleService vehicleService, UserService userService) {
        this.vehicleService = vehicleService;
        this.userService = userService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<VehicleDto> getVehicle(@PathVariable Long id) {
        return vehicleService.getVehicle(id)//
                .map(ResponseEntity::ok)//
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/all")
    public ResponseEntity<List<VehicleDto>> getAllVehicles() {
        return ResponseUtil.createListOkResponse(vehicleService.getAllVehicles());
    }

    @PostMapping("")
    public ResponseEntity<VehicleDto> addVehicle(@RequestBody VehicleDto vehicleDto) {
        VehicleDto result = vehicleService.addVehicle(vehicleDto, userService.getUserWithAuthoritiesOrFail());
        return ResponseEntity.created(URIUtil.buildURI(String.format("/api/vehicle/%s", result.getId().toString())))//
                .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))//
                .body(result);
    }

    @PutMapping("/{id}")
    public ResponseEntity<VehicleDto> editVehicle(@PathVariable Long id, @RequestBody VehicleDto vehicleDto) {
        return vehicleService.editVehicle(id, vehicleDto)//
                .map(i -> ResponseEntity.ok()//
                        .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, i.getId().toString()))//
                        .body(i)//
                )//
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<VehicleDto> deleteVehicle(@PathVariable Long id) {
        return vehicleService.deleteVehicle(id)//
                .map(v -> ResponseEntity.ok()//
                        .headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString()))//
                        .body(v)//
                )//
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
