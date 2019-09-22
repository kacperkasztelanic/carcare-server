package com.kasztelanic.carcare.web.rest;

import com.kasztelanic.carcare.repository.VehicleRepository;
import com.kasztelanic.carcare.service.EventsService;
import com.kasztelanic.carcare.service.dto.ForthcomingEvent;
import com.kasztelanic.carcare.service.dto.PeriodVehicle;
import com.kasztelanic.carcare.service.mapper.VehicleRichMapper;
import com.kasztelanic.carcare.web.rest.util.ResponseUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

@RestController
@RequestMapping("/api/events")
public class EventsResource {

    @Autowired
    private EventsService eventsService;
    @Autowired
    private VehicleRepository vehicleRepository;
    @Autowired
    private VehicleRichMapper vehicleRichMapper;

    @Transactional
    @PostMapping("")
    public ResponseEntity<List<ForthcomingEvent>> findForthcomingEvents(@RequestBody List<PeriodVehicle> periodVehicles) {
        Map<Long, PeriodVehicle> periodVehiclesByVehicleId = periodVehicles.stream()
                .collect(Collectors.toMap(PeriodVehicle::getVehicleId, Function.identity()));
        Set<Long> vehicleIds = periodVehicles.stream().map(PeriodVehicle::getVehicleId).collect(Collectors.toSet());
        List<ForthcomingEvent> list = vehicleRepository.findAllByIdAndOwnerIsCurrentUser(vehicleIds).stream()
                .map(vehicleRichMapper::vehicleToVehicleDto)
                .map(v -> eventsService.findForthcomingEvents(periodVehiclesByVehicleId.get(v.getId()), v))
                .flatMap(Collection::stream).sorted().collect(Collectors.toList());
        return ResponseUtil.createListOkResponse(list);
    }
}
