package com.kasztelanic.carcare.web.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kasztelanic.carcare.service.MailService;

@RestController
@RequestMapping("/mail")
public class DummyMailResource {

    @Autowired
    private MailService mailService;

    @GetMapping("/send")
    public String send() {
        mailService.sendEmail("kcpr51@hotmail.com", "Test", "To jest email testowy", false, false);
        return "gesendet";
    }
}
