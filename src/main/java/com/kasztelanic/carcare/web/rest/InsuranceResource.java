package com.kasztelanic.carcare.web.rest;

import com.kasztelanic.carcare.service.InsuranceService;
import com.kasztelanic.carcare.service.dto.InsuranceDto;
import com.kasztelanic.carcare.web.rest.util.HeaderUtil;
import com.kasztelanic.carcare.web.rest.util.ResponseUtil;
import com.kasztelanic.carcare.web.rest.util.UriUtil;

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
@RequestMapping("/api/insurance")
public class InsuranceResource {

    private static final String ENTITY_NAME = "insurance";

    private final InsuranceService insuranceService;

    @Autowired
    public InsuranceResource(InsuranceService insuranceService) {
        this.insuranceService = insuranceService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<InsuranceDto> getInsurance(@PathVariable Long id) {
        return insuranceService.getInsurance(id)//
                .map(ResponseEntity::ok)//
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/all/{vehicleId}")
    public ResponseEntity<List<InsuranceDto>> getAllInsurances(@PathVariable Long vehicleId) {
        return ResponseUtil.createListOkResponse(insuranceService.getAllInsurances(vehicleId));
    }

    @PostMapping("/{vehicleId}")
    public ResponseEntity<InsuranceDto> addInsurance(@PathVariable Long vehicleId,
            @RequestBody InsuranceDto insuranceDto) {
        return insuranceService.addInsurance(vehicleId, insuranceDto)//
                .map(i -> ResponseEntity.created(UriUtil.buildURI(
                        String.format("/api/insurance/%s/%s", vehicleId.toString(), i.getId().toString())))//
                        .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, i.getId().toString()))//
                        .body(i))//
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<InsuranceDto> editInsurance(@PathVariable Long id, @RequestBody InsuranceDto insuranceDto) {
        return insuranceService.editInsurance(id, insuranceDto)//
                .map(i -> ResponseEntity.ok()//
                        .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, i.getId().toString()))//
                        .body(i))//
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<InsuranceDto> deleteInsurance(@PathVariable Long id) {
        return insuranceService.deleteInsurance(id)//
                .map(i -> ResponseEntity.ok()//
                        .headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString()))//
                        .body(i))//
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
