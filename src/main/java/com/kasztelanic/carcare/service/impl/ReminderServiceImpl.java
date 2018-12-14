package com.kasztelanic.carcare.service.impl;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.google.common.collect.ImmutableSet;
import com.kasztelanic.carcare.domain.Insurance;
import com.kasztelanic.carcare.domain.User;
import com.kasztelanic.carcare.domain.Vehicle;
import com.kasztelanic.carcare.repository.InsuranceRepository;
import com.kasztelanic.carcare.service.MailService;
import com.kasztelanic.carcare.service.ReminderService;

@Service
public class ReminderServiceImpl implements ReminderService {

    @Autowired
    private InsuranceRepository insuranceRepository;

    @Autowired
    private MailService mailService;

    private Set<Integer> daysBefore = ImmutableSet.of(3, 7, 14, 30);

    @Scheduled(cron = "0 0 8 * * *")
    public void sendInsuranceReminders() {
        LocalDate now = LocalDate.now();
        Set<LocalDate> dates = daysBefore.stream().map(now::plusDays).collect(Collectors.toSet());
        List<Insurance> insurances = insuranceRepository.findByValidThruIn(dates);
        for (Insurance insurance : insurances) {
            int diff = (int) ChronoUnit.DAYS.between(now, insurance.getValidThru());
            Vehicle vehicle = insurance.getVehicle();
            User owner = vehicle.getOwner();
            mailService.sendInsuranceReminderEmail(owner, vehicle, insurance, diff);
        }
    }
}
