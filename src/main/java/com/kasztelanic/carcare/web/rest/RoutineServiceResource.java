package com.kasztelanic.carcare.web.rest;

import com.kasztelanic.carcare.service.RoutineServiceService;
import com.kasztelanic.carcare.service.dto.RoutineServiceDto;
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
@RequestMapping("/api/routine-service")
public class RoutineServiceResource {

    private static final String ENTITY_NAME = "routineService";

    private final RoutineServiceService routineServiceService;

    @Autowired
    public RoutineServiceResource(RoutineServiceService routineServiceService) {
        this.routineServiceService = routineServiceService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoutineServiceDto> getRoutineService(@PathVariable Long id) {
        return routineServiceService.getRoutineService(id)//
            .map(ResponseEntity::ok)//
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/all/{vehicleId}")
    public ResponseEntity<List<RoutineServiceDto>> getAllRoutineServices(@PathVariable Long vehicleId) {
        return ResponseUtil.createListOkResponse(routineServiceService.getAllRoutineServices(vehicleId));
    }

    @PostMapping("/{vehicleId}")
    public ResponseEntity<RoutineServiceDto> addRoutineService(@PathVariable Long vehicleId,
                                                               @RequestBody RoutineServiceDto routineServiceDto) {
        BiFunction<Long, RoutineServiceDto, URI> uriProvider = //
            (i, r) -> UriUtil.buildURI(String.format("/api/routine-service/%s/%s", i, r.getId()));
        return routineServiceService.addRoutineService(vehicleId, routineServiceDto)//
            .map(i -> ResponseEntity.created(uriProvider.apply(vehicleId, i))//
                .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, i.getId().toString()))//
                .body(i)//
            )//
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<RoutineServiceDto> editRoutineService(@PathVariable Long id,
                                                                @RequestBody RoutineServiceDto routineServiceDto) {
        return routineServiceService.editRoutineService(id, routineServiceDto)//
            .map(i -> ResponseEntity.ok()//
                .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, i.getId().toString()))//
                .body(i)//
            )//
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<RoutineServiceDto> deleteRoutineService(@PathVariable Long id) {
        return routineServiceService.deleteRoutineService(id)//
            .map(r -> ResponseEntity.ok()//
                .headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString()))//
                .body(r)//
            )//
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
