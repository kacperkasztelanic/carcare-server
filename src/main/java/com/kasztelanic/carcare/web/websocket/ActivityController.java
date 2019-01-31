package com.kasztelanic.carcare.web.websocket;

import java.security.Principal;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.kasztelanic.carcare.config.WebsocketConfiguration;
import com.kasztelanic.carcare.web.websocket.dto.ActivityDto;
import com.kasztelanic.carcare.web.websocket.dto.ActivityDto.ActivityDtoBuilder;

@Controller
public class ActivityController implements ApplicationListener<SessionDisconnectEvent> {

    private static final Logger log = LoggerFactory.getLogger(ActivityController.class);

    private final SimpMessageSendingOperations messagingTemplate;

    public ActivityController(SimpMessageSendingOperations messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/topic/activity")
    @SendTo("/topic/tracker")
    public ActivityDto sendActivity(@Payload ActivityDto activityDto, StompHeaderAccessor stompHeaderAccessor,
            Principal principal) {
        ActivityDtoBuilder builder = ActivityDto.builder();
        builder.page(activityDto.getPage());
        builder.userLogin(principal.getName());
        builder.sessionId(stompHeaderAccessor.getSessionId());
        builder.ipAddress(stompHeaderAccessor.getSessionAttributes().get(WebsocketConfiguration.IP_ADDRESS).toString());
        builder.time(Instant.now());
        ActivityDto newActivityDto = builder.build();
        log.debug("Sending user tracking data {}", newActivityDto);
        return newActivityDto;
    }

    @Override
    public void onApplicationEvent(SessionDisconnectEvent event) {
        ActivityDtoBuilder builder = ActivityDto.builder();
        builder.sessionId(event.getSessionId());
        builder.page("logout");
        messagingTemplate.convertAndSend("/topic/tracker", builder.build());
    }
}
