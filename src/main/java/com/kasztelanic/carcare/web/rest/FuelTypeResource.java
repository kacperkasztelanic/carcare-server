package com.kasztelanic.carcare.web.rest;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kasztelanic.carcare.domain.FuelType;
import com.kasztelanic.carcare.repository.FuelTypeRepository;
import com.kasztelanic.carcare.security.AuthoritiesConstants;
import com.kasztelanic.carcare.web.rest.util.HeaderUtil;
import com.kasztelanic.carcare.web.rest.util.ResponseUtil;
import com.kasztelanic.carcare.web.rest.util.URIUtil;

@RestController
@RequestMapping("/api/fuel-type")
public class FuelTypeResource {

    private static final String ENTITY_NAME = "fuel-type";

    @Autowired
    private FuelTypeRepository fuelTypeRepository;

    @Transactional
    @PostMapping("/{type}")
    @PreAuthorize("hasRole(\"" + AuthoritiesConstants.ADMIN + "\")")
    public ResponseEntity<String> addFuelType(@PathVariable String type) {
        FuelType fuelType = fuelTypeRepository.save(FuelType.of(type.toUpperCase()));
        return ResponseEntity.created(URIUtil.buildURI(String.format("/api/fuelType/%s", fuelType.getType())))
                .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, fuelType.getType()))
                .body(fuelType.getType());
    }

    @Transactional
    @DeleteMapping("/{type}")
    @PreAuthorize("hasRole(\"" + AuthoritiesConstants.ADMIN + "\")")
    public ResponseEntity<Void> deleteRoutineService(@PathVariable String type) {
        Optional<FuelType> fuelType = fuelTypeRepository.findByType(type);
        if (fuelType.isPresent()) {
            fuelTypeRepository.delete(fuelType.get());
            return ResponseEntity.ok()
                    .headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, fuelType.get().getType())).build();
        }
        return ResponseEntity.notFound().build();
    }

    @Transactional
    @GetMapping("")
    public ResponseEntity<List<String>> getFuelTypes() {
        List<String> list = fuelTypeRepository.findAll().stream().map(FuelType::getType).collect(Collectors.toList());
        return ResponseUtil.createListOkResponse(list);
    }
}