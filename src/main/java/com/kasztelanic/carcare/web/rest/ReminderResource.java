package com.kasztelanic.carcare.web.rest;

import com.kasztelanic.carcare.security.AuthoritiesConstants;
import com.kasztelanic.carcare.service.ReminderService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reminder")
public class ReminderResource {

    private final ReminderService reminderService;

    @Autowired
    public ReminderResource(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @GetMapping("/send")
    @PreAuthorize("hasRole(\"" + AuthoritiesConstants.ADMIN + "\")")
    public void invokeRemindersDispatch() {
        reminderService.sendReminders();
    }
}
