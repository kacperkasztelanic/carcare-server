package com.kasztelanic.carcare.service.dto;

import java.time.LocalDate;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Builder
@EqualsAndHashCode
@ToString
public class ForthcomingEvent implements Comparable<ForthcomingEvent> {

    @Getter
    private final Long vehicleId;
    @Getter
    private final String type;
    @Getter
    private final String details;
    @Getter
    private final LocalDate dateThru;
    @Getter
    private final int mileageThru;

    @Override
    public int compareTo(ForthcomingEvent o) {
        int result = dateThru.compareTo(o.dateThru);
        if (result == 0 && mileageThru > 0 && o.mileageThru > 0) {
            return mileageThru - o.mileageThru;
        }
        return result;
    }
}