package com.kasztelanic.carcare.web.rest;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class Now {

    @GetMapping("/now")
    public Instant now() {
        System.out.println("now");
        return Instant.now();
    }
}
