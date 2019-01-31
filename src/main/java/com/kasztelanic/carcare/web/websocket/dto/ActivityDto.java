package com.kasztelanic.carcare.web.websocket.dto;

import java.time.Instant;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Builder
@EqualsAndHashCode
public class ActivityDto {

    @Getter
    private String sessionId;
    @Getter
    private String userLogin;
    @Getter
    private String ipAddress;
    @Getter
    private String page;
    @Getter
    private Instant time;

    @Override
    public String toString() {
        return "ActivityDto(" + userLogin + ", " + page + ", " + time + ")";
    }
}
