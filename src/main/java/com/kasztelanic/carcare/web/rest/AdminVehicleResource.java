package com.kasztelanic.carcare.web.rest;

import com.kasztelanic.carcare.security.AuthoritiesConstants;
import com.kasztelanic.carcare.service.AdminVehicleService;
import com.kasztelanic.carcare.service.dto.AdminVehicleDto;
import com.kasztelanic.carcare.web.rest.util.HeaderUtil;
import com.kasztelanic.carcare.web.rest.util.PaginationUtil;
import com.kasztelanic.carcare.web.rest.util.ResponseUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/admin/vehicles")
@PreAuthorize("hasRole(\"" + AuthoritiesConstants.ADMIN + "\")")
public class AdminVehicleResource {

    private final AdminVehicleService adminVehicleService;

    @Autowired
    public AdminVehicleResource(AdminVehicleService adminVehicleService) {
        this.adminVehicleService = adminVehicleService;
    }

    @GetMapping("/archived")
    public ResponseEntity<List<AdminVehicleDto>> getArchivedVehicles(Pageable pageable) {
        Page<AdminVehicleDto> page = adminVehicleService.findArchived(pageable);
        HttpHeaders headers = PaginationUtil
            .generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok()
            .headers(headers)
            .body(page.getContent());
    }

    @PutMapping("/{id}/restore")
    public ResponseEntity<AdminVehicleDto> restoreVehicle(@PathVariable Long id) {
        return ResponseUtil.wrapOrNotFound(adminVehicleService.restoreVehicle(id),
            HeaderUtil.createEntityUpdateAlert("vehicle", id.toString()));
    }
}
