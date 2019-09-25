package com.kasztelanic.carcare.web.rest;

import com.kasztelanic.carcare.service.RefuelService;
import com.kasztelanic.carcare.service.dto.RefuelDto;
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
@RequestMapping("/api/refuel")
public class RefuelResource {

    private static final String ENTITY_NAME = "refuel";

    private final RefuelService refuelService;

    @Autowired
    public RefuelResource(RefuelService refuelService) {
        this.refuelService = refuelService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<RefuelDto> getRefuel(@PathVariable Long id) {
        return refuelService.getRefuel(id)//
                .map(ResponseEntity::ok)//
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/all/{vehicleId}")
    public ResponseEntity<List<RefuelDto>> getAllRefuels(@PathVariable Long vehicleId) {
        return ResponseUtil.createListOkResponse(refuelService.getAllRefuels(vehicleId));
    }

    @PostMapping("/{vehicleId}")
    public ResponseEntity<RefuelDto> addRefuel(@PathVariable Long vehicleId, @RequestBody RefuelDto refuelDto) {
        return refuelService.addRefuel(vehicleId, refuelDto)//
                .map(i -> ResponseEntity.created(URIUtil.buildURI(
                        String.format("/api/refuel/%s/%s", vehicleId.toString(), i.getId().toString())))//
                        .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, i.getId().toString()))//
                        .body(i))//
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<RefuelDto> editRefuel(@PathVariable Long id, @RequestBody RefuelDto refuelDto) {
        return refuelService.editRefuel(id, refuelDto)//
                .map(i -> ResponseEntity.ok()//
                        .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, i.getId().toString()))//
                        .body(i))//
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<RefuelDto> deleteRefuel(@PathVariable Long id) {
        return refuelService.deleteRefuel(id)//
                .map(r -> ResponseEntity.ok()//
                        .headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString()))//
                        .body(r))//
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
