package com.kasztelanic.carcare.web.rest;

import com.kasztelanic.carcare.service.EventService;
import com.kasztelanic.carcare.service.dto.ForthcomingEvent;
import com.kasztelanic.carcare.service.dto.PeriodVehicle;
import com.kasztelanic.carcare.web.rest.util.ResponseUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/events")
public class EventResource {

    private final EventService eventsService;

    @Autowired
    public EventResource(EventService eventsService) {
        this.eventsService = eventsService;
    }

    @PostMapping("")
    public ResponseEntity<List<ForthcomingEvent>> findForthcomingEvents(
            @RequestBody List<PeriodVehicle> periodVehicles) {
        return ResponseUtil.createListOkResponse(eventsService.findForthcomingEvents(periodVehicles));
    }
}
