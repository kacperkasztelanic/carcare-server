package com.kasztelanic.carcare.web.rest;

import com.kasztelanic.carcare.service.InspectionService;
import com.kasztelanic.carcare.service.dto.InspectionDto;
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
@RequestMapping("/api/inspection")
public class InspectionResource {

    private static final String ENTITY_NAME = "inspection";

    private final InspectionService inspectionService;

    @Autowired
    public InspectionResource(InspectionService inspectionService) {
        this.inspectionService = inspectionService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<InspectionDto> getInspection(@PathVariable Long id) {
        return inspectionService.getInspection(id)//
                .map(ResponseEntity::ok)//
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/all/{vehicleId}")
    public ResponseEntity<List<InspectionDto>> getAllInspections(@PathVariable Long vehicleId) {
        return ResponseUtil.createListOkResponse(inspectionService.getAllInspections(vehicleId));
    }

    @PostMapping("/{vehicleId}")
    public ResponseEntity<InspectionDto> addInspection(@PathVariable Long vehicleId,
            @RequestBody InspectionDto inspectionDto) {
        return inspectionService.addInspection(vehicleId, inspectionDto)//
                .map(i -> ResponseEntity.created(UriUtil.buildURI(
                        String.format("/api/inspection/%s/%s", vehicleId.toString(), i.getId().toString())))//
                        .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, i.getId().toString()))//
                        .body(i))//
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<InspectionDto> editInspection(@PathVariable Long id,
            @RequestBody InspectionDto inspectionDto) {
        return inspectionService.editInspection(id, inspectionDto)//
                .map(i -> ResponseEntity.ok()//
                        .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, i.getId().toString()))//
                        .body(i))//
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<InspectionDto> deleteInspection(@PathVariable Long id) {
        return inspectionService.deleteInspection(id)//
                .map(i -> ResponseEntity.ok()//
                        .headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString()))//
                        .body(i))//
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
