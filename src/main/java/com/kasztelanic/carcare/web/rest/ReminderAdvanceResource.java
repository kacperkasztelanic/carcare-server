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

import com.kasztelanic.carcare.domain.ReminderAdvance;
import com.kasztelanic.carcare.repository.ReminderAdvanceRepository;
import com.kasztelanic.carcare.security.AuthoritiesConstants;
import com.kasztelanic.carcare.web.rest.util.HeaderUtil;
import com.kasztelanic.carcare.web.rest.util.ResponseUtil;
import com.kasztelanic.carcare.web.rest.util.URIUtil;

@RestController
@RequestMapping("/api/reminder-advance")
public class ReminderAdvanceResource {

    private static final String ENTITY_NAME = "reminder-advance";

    @Autowired
    private ReminderAdvanceRepository reminderAdvanceRepository;

    @Transactional
    @PostMapping("/{type}")
    @PreAuthorize("hasRole(\"" + AuthoritiesConstants.ADMIN + "\")")
    public ResponseEntity<Integer> addReminderAdvance(@PathVariable Integer days) {
        ReminderAdvance reminderAdvance = reminderAdvanceRepository.save(ReminderAdvance.of(days));
        return ResponseEntity
                .created(URIUtil.buildURI(String.format("/api/insuranceType/%s", reminderAdvance.getDays())))
                .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, String.valueOf(reminderAdvance.getDays())))
                .body(reminderAdvance.getDays());
    }

    @Transactional
    @DeleteMapping("/{type}")
    @PreAuthorize("hasRole(\"" + AuthoritiesConstants.ADMIN + "\")")
    public ResponseEntity<Void> deleteReminderAdvance(@PathVariable Integer days) {
        Optional<ReminderAdvance> reminderAdvance = reminderAdvanceRepository.findByDays(days);
        if (reminderAdvance.isPresent()) {
            reminderAdvanceRepository.delete(reminderAdvance.get());
            return ResponseEntity.ok().headers(
                    HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, String.valueOf(reminderAdvance.get().getDays())))
                    .build();
        }
        return ResponseEntity.notFound().build();
    }

    @Transactional
    @GetMapping("")
    public ResponseEntity<List<Integer>> getReminderAdvances() {
        List<Integer> list = reminderAdvanceRepository.findAll().stream().map(ReminderAdvance::getDays)
                .collect(Collectors.toList());
        return ResponseUtil.createListOkResponse(list);
    }
}
