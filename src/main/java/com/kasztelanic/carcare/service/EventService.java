package com.kasztelanic.carcare.service;

import com.kasztelanic.carcare.service.dto.ForthcomingEvent;
import com.kasztelanic.carcare.service.dto.PeriodVehicle;

import java.util.Collection;
import java.util.List;

public interface EventService {

    List<ForthcomingEvent> findForthcomingEvents(Collection<PeriodVehicle> periodVehicles);
}
