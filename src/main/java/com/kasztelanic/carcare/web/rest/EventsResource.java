package com.kasztelanic.carcare.web.rest;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.codahale.metrics.annotation.Timed;
import com.kasztelanic.carcare.domain.User;
import com.kasztelanic.carcare.repository.VehicleRepository;
import com.kasztelanic.carcare.service.EventsService;
import com.kasztelanic.carcare.service.UserService;
import com.kasztelanic.carcare.service.dto.ForthcomingEvent;
import com.kasztelanic.carcare.service.dto.PeriodVehicle;
import com.kasztelanic.carcare.service.mapper.VehicleRichMapper;

@RestController
@RequestMapping("/api/events")
public class EventsResource {

    @Autowired
    private EventsService eventsService;
    @Autowired
    private VehicleRepository vehicleRepository;
    @Autowired
    private VehicleRichMapper vehicleRichMapper;
    @Autowired
    private UserService userService;

    @Timed
    @Transactional
    @PostMapping("")
    public List<ForthcomingEvent> findForthcomingEvents(@RequestBody List<PeriodVehicle> periodVehicles) {
        User user = userService.getUserWithAuthorities().orElseThrow(IllegalStateException::new);
        Map<Long, PeriodVehicle> periodVehiclesByVehicleId = periodVehicles.stream()
                .collect(Collectors.toMap(PeriodVehicle::getVehicleId, Function.identity()));
        Set<Long> vehicleIds = periodVehicles.stream().map(PeriodVehicle::getVehicleId).collect(Collectors.toSet());
        return vehicleRepository.findAllByIdAndOwnerIsCurrentUser(vehicleIds).stream()
                .map(vehicleRichMapper::vehicleToVehicleDto)
                .map(v -> eventsService.findForthcomingEvents(periodVehiclesByVehicleId.get(v.getId()), v, user))
                .flatMap(Collection::stream).sorted().collect(Collectors.toList());
    }
}
