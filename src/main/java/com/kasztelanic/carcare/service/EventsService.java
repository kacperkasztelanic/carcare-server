package com.kasztelanic.carcare.service;

import java.util.List;

import com.kasztelanic.carcare.domain.User;
import com.kasztelanic.carcare.service.dto.ForthcomingEvent;
import com.kasztelanic.carcare.service.dto.PeriodVehicle;
import com.kasztelanic.carcare.service.dto.VehicleRichDto;

public interface EventsService {

    List<ForthcomingEvent> findForthcomingEvents(PeriodVehicle periodVehicle, VehicleRichDto vehicle);
}
