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

import com.kasztelanic.carcare.domain.InsuranceType;
import com.kasztelanic.carcare.repository.InsuranceTypeRepository;
import com.kasztelanic.carcare.security.AuthoritiesConstants;
import com.kasztelanic.carcare.web.rest.util.HeaderUtil;
import com.kasztelanic.carcare.web.rest.util.URIUtil;

@RestController
@RequestMapping("/api/insurance-type")
@PreAuthorize("hasRole(\"" + AuthoritiesConstants.ADMIN + "\")")
public class InsuranceTypeResource {

    private static final String ENTITY_NAME = "insurance-type";

    @Autowired
    private InsuranceTypeRepository insuranceTypeRepository;

    @Transactional
    @PostMapping("/{type}")
    public ResponseEntity<String> addInsuranceType(@PathVariable String type) {
        InsuranceType insuranceType = insuranceTypeRepository.save(InsuranceType.of(type.toUpperCase()));
        return ResponseEntity.created(URIUtil.buildURI(String.format("/api/insuranceType/%s", insuranceType.getType())))
                .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, insuranceType.getType()))
                .body(insuranceType.getType());
    }

    @Transactional
    @DeleteMapping("/{type}")
    public ResponseEntity<Void> deleteRoutineService(@PathVariable String type) {
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
    public List<String> getInsuranceTypes(@PathVariable String type) {
        return insuranceTypeRepository.findAll().stream().map(InsuranceType::getType).collect(Collectors.toList());
    }
}
