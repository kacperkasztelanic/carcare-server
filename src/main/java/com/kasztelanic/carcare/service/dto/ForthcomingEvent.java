package com.kasztelanic.carcare.service.dto;

import com.google.common.collect.ImmutableMap;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;

import static java.util.function.Function.identity;

@Builder
@EqualsAndHashCode
@ToString
public class ForthcomingEvent implements Comparable<ForthcomingEvent> {

    @Getter
    private final Long vehicleId;
    @Getter
    private final EventType eventType;
    @Getter
    private final String details;
    @Getter
    private final LocalDate dateThru;
    @Getter
    private final Integer mileageThru;

    @Override
    public int compareTo(ForthcomingEvent o) {
        int result = dateThru.compareTo(o.dateThru);
        if (result == 0 && mileageThru > 0 && o.mileageThru > 0) {
            return mileageThru - o.mileageThru;
        }
        return result;
    }

    public enum EventType {

        INSURANCE(InsuranceDto.class), INSPECTION(InspectionDto.class), SERVICE(RoutineServiceDto.class);

        @Getter
        private final Class<?> clazz;

        EventType(Class<?> clazz) {
            this.clazz = clazz;
        }

        private static final Map<Class<?>, EventType> CLAZZ_MAP = Arrays.stream(EventType.values())//
            .collect(ImmutableMap.toImmutableMap(EventType::getClazz, identity()));

        public static EventType fromClass(Class<?> clazz) {
            return CLAZZ_MAP.get(clazz);
        }
    }
}
