package com.kasztelanic.carcare.service.dto;

import java.time.LocalDate;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

@Value
@Builder
@EqualsAndHashCode(of = { "id" })
@ToString(of = { "validThru", "vehicleEvent" })
public class InsuranceDto implements HasVehicleEvent, HasCost {

     Long id;
     VehicleEventDto vehicleEvent;
     LocalDate validFrom;
     LocalDate validThru;
     Integer costInCents;
     String number;
     String insurer;
     String details;
     InsuranceTypeDto insuranceType;
     Long vehicleId;
}
