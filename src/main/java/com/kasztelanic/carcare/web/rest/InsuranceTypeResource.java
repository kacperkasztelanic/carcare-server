package com.kasztelanic.carcare.web.rest;

import java.util.List;
import java.util.Locale;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kasztelanic.carcare.domain.InsuranceType;
import com.kasztelanic.carcare.domain.User;
import com.kasztelanic.carcare.repository.InsuranceTypeRepository;
import com.kasztelanic.carcare.security.AuthoritiesConstants;
import com.kasztelanic.carcare.service.UserService;
import com.kasztelanic.carcare.service.dto.InsuranceTypeDto;
import com.kasztelanic.carcare.service.dto.InsuranceTypeRequest;
import com.kasztelanic.carcare.service.mapper.InsuranceTypeMapper;
import com.kasztelanic.carcare.web.rest.util.HeaderUtil;
import com.kasztelanic.carcare.web.rest.util.ResponseUtil;
import com.kasztelanic.carcare.web.rest.util.UriUtil;

//TODO extract service
@RestController
@RequestMapping("/api/insurance-type")
public class InsuranceTypeResource {

    private static final String ENTITY_NAME = "insurance-type";

    @Autowired
    private InsuranceTypeRepository insuranceTypeRepository;
    @Autowired
    private InsuranceTypeMapper insuranceTypeMapper;
    @Autowired
    private UserService userService;

    @Transactional
    @PostMapping("")
    @PreAuthorize("hasRole(\"" + AuthoritiesConstants.ADMIN + "\")")
    public ResponseEntity<String> addInsuranceType(@RequestBody InsuranceTypeRequest insuranceTypeRequest) {
        InsuranceType insuranceType = insuranceTypeRepository
                .save(InsuranceType.of(insuranceTypeRequest.getType().toUpperCase(),
                        insuranceTypeRequest.getEnglishTranslation(), insuranceTypeRequest.getPolishTranslation()));
        return ResponseEntity.created(UriUtil.buildURI(String.format("/api/insuranceType/%s", insuranceType.getType())))
                .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, insuranceType.getType()))
                .body(insuranceType.getType());
    }

    @Transactional
    @DeleteMapping("/{type}")
    @PreAuthorize("hasRole(\"" + AuthoritiesConstants.ADMIN + "\")")
    public ResponseEntity<Void> deleteInsuranceType(@PathVariable String type) {
        Optional<InsuranceType> insuranceType = insuranceTypeRepository.findByType(type);
        if (insuranceType.isPresent()) {
            insuranceTypeRepository.delete(insuranceType.get());
            return ResponseEntity.ok()
                    .headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, insuranceType.get().getType())).build();
        }
        return ResponseEntity.notFound().build();
    }

    @Transactional
    @GetMapping("")
    public ResponseEntity<List<InsuranceTypeDto>> getInsuranceTypes() {
        User user = userService.getUserWithAuthorities().orElseThrow(IllegalStateException::new);
        List<InsuranceTypeDto> list = insuranceTypeRepository.findAll().stream().map(
                t -> insuranceTypeMapper.insuranceTypeToInsuranceTypeDto(t, Locale.forLanguageTag(user.getLangKey())))
                .collect(Collectors.toList());
        return ResponseUtil.createListOkResponse(list);
    }
}
