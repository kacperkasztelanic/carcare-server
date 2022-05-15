package com.kasztelanic.carcare.web.rest;

import com.kasztelanic.carcare.service.AuditEventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.PaginationUtil;
import tech.jhipster.web.util.ResponseUtil;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * REST controller for getting the {@link AuditEvent}s.
 */
@RestController
@RequestMapping("/management/audits")
public class AuditResource {

    private final AuditEventService auditEventService;

    @Autowired
    public AuditResource(AuditEventService auditEventService) {
        this.auditEventService = auditEventService;
    }

    /**
     * {@code GET /audits} : get a page of {@link AuditEvent}s.
     *
     * @param pageable the pagination information.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of {@link AuditEvent}s in body.
     */
    @GetMapping
    public ResponseEntity<List<AuditEvent>> getAll(Pageable pageable) {
        Page<AuditEvent> page = auditEventService.findAll(pageable);
        HttpHeaders headers = PaginationUtil
            .generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok()//
            .headers(headers)//
            .body(page.getContent());
    }

    /**
     * {@code GET  /audits} : get a page of {@link AuditEvent} between the {@code fromDate} and {@code toDate}.
     *
     * @param fromDate the start of the time period of {@link AuditEvent} to get.
     * @param toDate   the end of the time period of {@link AuditEvent} to get.
     * @param pageable the pagination information.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of {@link AuditEvent} in body.
     */
    @GetMapping(params = {"fromDate", "toDate"})
    public ResponseEntity<List<AuditEvent>> getByDates(@RequestParam(value = "fromDate") LocalDate fromDate,
                                                       @RequestParam(value = "toDate") LocalDate toDate, Pageable pageable) {
        Page<AuditEvent> page = auditEventService.findByDates(fromDate.atStartOfDay(ZoneId.systemDefault()).toInstant(),
            toDate.atStartOfDay(ZoneId.systemDefault()).plusDays(1).toInstant(), pageable);
        HttpHeaders headers = PaginationUtil
            .generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok()//
            .headers(headers)//
            .body(page.getContent());
    }

    /**
     * {@code GET  /audits/:id} : get an {@link AuditEvent} by id.
     *
     * @param id the id of the entity to get.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the {@link AuditEvent} in body, or status
     * {@code 404 (Not Found)}.
     */
    @GetMapping("/{id:.+}")
    public ResponseEntity<AuditEvent> get(@PathVariable Long id) {
        return ResponseUtil.wrapOrNotFound(auditEventService.find(id));
    }
}
