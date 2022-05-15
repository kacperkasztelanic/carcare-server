package com.kasztelanic.carcare.web.rest;

import com.kasztelanic.carcare.domain.FuelType;
import com.kasztelanic.carcare.domain.User;
import com.kasztelanic.carcare.repository.FuelTypeRepository;
import com.kasztelanic.carcare.security.AuthoritiesConstants;
import com.kasztelanic.carcare.service.UserService;
import com.kasztelanic.carcare.service.dto.FuelTypeDto;
import com.kasztelanic.carcare.service.dto.FuelTypeRequest;
import com.kasztelanic.carcare.service.mapper.FuelTypeMapper;
import com.kasztelanic.carcare.web.rest.util.HeaderUtil;
import com.kasztelanic.carcare.web.rest.util.ResponseUtil;
import com.kasztelanic.carcare.web.rest.util.UriUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

//TODO extract service
@RestController
@RequestMapping("/api/fuel-type")
public class FuelTypeResource {

    private static final String ENTITY_NAME = "fuel-type";

    private final FuelTypeRepository fuelTypeRepository;
    private final FuelTypeMapper fuelTypeMapper;
    private final UserService userService;

    @Autowired
    public FuelTypeResource(FuelTypeRepository fuelTypeRepository, FuelTypeMapper fuelTypeMapper,
                            UserService userService) {
        this.fuelTypeRepository = fuelTypeRepository;
        this.fuelTypeMapper = fuelTypeMapper;
        this.userService = userService;
    }

    @Transactional
    @PostMapping("")
    @PreAuthorize("hasRole(\"" + AuthoritiesConstants.ADMIN + "\")")
    public ResponseEntity<String> addFuelType(@RequestBody FuelTypeRequest fuelTypeDto) {
        FuelType fuelType = fuelTypeRepository.save(//
            FuelType.of(fuelTypeDto.getType().toUpperCase(),//
                fuelTypeDto.getEnglishTranslation(),//
                fuelTypeDto.getPolishTranslation()//
            )//
        );
        return ResponseEntity.created(UriUtil.buildURI(String.format("/api/fuelType/%s", fuelType.getType())))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, fuelType.getType()))
            .body(fuelType.getType());
    }

    @Transactional
    @DeleteMapping("/{type}")
    @PreAuthorize("hasRole(\"" + AuthoritiesConstants.ADMIN + "\")")
    public ResponseEntity<Void> deleteFuelType(@PathVariable String type) {
        Optional<FuelType> fuelType = fuelTypeRepository.findByType(type);
        if (fuelType.isPresent()) {
            fuelTypeRepository.delete(fuelType.get());
            return ResponseEntity.ok()
                .headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, fuelType.get().getType()))//
                .build();
        }
        return ResponseEntity.notFound().build();
    }

    @Transactional
    @GetMapping("")
    public ResponseEntity<List<FuelTypeDto>> getFuelTypes() {
        User user = userService.getUserWithAuthoritiesOrFail();
        List<FuelTypeDto> list = fuelTypeRepository.findAll().stream()
            .map(t -> fuelTypeMapper.fuelTypeToFuelTypeDto(t, Locale.forLanguageTag(user.getLangKey())))
            .collect(Collectors.toList());
        return ResponseUtil.createListOkResponse(list);
    }
}
