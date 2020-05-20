package com.kasztelanic.carcare.web.rest;

import com.kasztelanic.carcare.service.RepairService;
import com.kasztelanic.carcare.service.dto.RepairDto;
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

import java.net.URI;
import java.util.List;
import java.util.function.BiFunction;

@RestController
@RequestMapping("/api/repair")
public class RepairResource {

    private static final String ENTITY_NAME = "repair";

    private final RepairService repairService;

    @Autowired
    public RepairResource(RepairService repairService) {
        this.repairService = repairService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<RepairDto> getRepair(@PathVariable Long id) {
        return repairService.getRepair(id)//
                .map(ResponseEntity::ok)//
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/all/{vehicleId}")
    public ResponseEntity<List<RepairDto>> getAllRepairs(@PathVariable Long vehicleId) {
        return ResponseUtil.createListOkResponse(repairService.getAllRepairs(vehicleId));
    }

    @PostMapping("/{vehicleId}")
    public ResponseEntity<RepairDto> addRepair(@PathVariable Long vehicleId, @RequestBody RepairDto repairDto) {
        BiFunction<Long, RepairDto, URI> uriProvider = //
                (i, r) -> UriUtil.buildURI(String.format("/api/repair/%s/%s", i, r.getId()));
        return repairService.addRepair(vehicleId, repairDto)//
                .map(i -> ResponseEntity.created(uriProvider.apply(vehicleId, i))//
                        .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, i.getId().toString()))//
                        .body(i)//
                )//
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<RepairDto> editRepair(@PathVariable Long id, @RequestBody RepairDto repairDto) {
        return repairService.editRepair(id, repairDto)//
                .map(i -> ResponseEntity.ok()//
                        .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, i.getId().toString()))//
                        .body(i)//
                )//
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<RepairDto> deleteRepair(@PathVariable Long id) {
        return repairService.deleteRepair(id)//
                .map(r -> ResponseEntity.ok()//
                        .headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString()))//
                        .body(r)//
                )//
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
