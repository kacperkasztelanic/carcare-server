package com.kasztelanic.carcare.service.impl;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.kasztelanic.carcare.domain.Inspection;
import com.kasztelanic.carcare.domain.Insurance;
import com.kasztelanic.carcare.domain.ReminderAdvance;
import com.kasztelanic.carcare.domain.RoutineService;
import com.kasztelanic.carcare.domain.User;
import com.kasztelanic.carcare.domain.Vehicle;
import com.kasztelanic.carcare.repository.InspectionRepository;
import com.kasztelanic.carcare.repository.InsuranceRepository;
import com.kasztelanic.carcare.repository.ReminderAdvanceRepository;
import com.kasztelanic.carcare.repository.RoutineServiceRepository;
import com.kasztelanic.carcare.service.MailService;
import com.kasztelanic.carcare.service.ReminderService;

@Service
public class ReminderServiceImpl implements ReminderService {

    private final Logger log = LoggerFactory.getLogger(ReminderServiceImpl.class);

    @Autowired
    private ReminderAdvanceRepository reminderAdvanceRepository;
    @Autowired
    private InsuranceRepository insuranceRepository;
    @Autowired
    private InspectionRepository inspectionRepository;
    @Autowired
    private RoutineServiceRepository routineServiceRepository;
    @Autowired
    private MailService mailService;

    @Override
    @Scheduled(cron = "0 0 8 * * *")
    public void sendReminders() {
        log.info("Email notification dispatch invoked");
        LocalDate now = LocalDate.now();
        Set<LocalDate> dates = reminderAdvanceRepository.findAll().stream().map(ReminderAdvance::getDays)
                .map(now::plusDays).collect(Collectors.toSet());
        sendInsuranceReminders(dates, now);
        sendInspectionReminders(dates, now);
        sendRoutineServiceReminders(dates, now);
        log.info("Email notifications dispatched");
    }

    @Override
    public void sendInsuranceReminders(Set<LocalDate> dates, LocalDate now) {
        List<Insurance> insurances = insuranceRepository.findByValidThruIn(dates);
        for (Insurance insurance : insurances) {
            int diff = (int) ChronoUnit.DAYS.between(now, insurance.getValidThru());
            Vehicle vehicle = insurance.getVehicle();
            User owner = vehicle.getOwner();
            mailService.sendInsuranceReminderEmail(owner, vehicle, insurance, diff);
        }
    }

    @Override
    public void sendInspectionReminders(Set<LocalDate> dates, LocalDate now) {
        List<Inspection> inspections = inspectionRepository.findByValidThruIn(dates);
        for (Inspection insurance : inspections) {
            int diff = (int) ChronoUnit.DAYS.between(now, insurance.getValidThru());
            Vehicle vehicle = insurance.getVehicle();
            User owner = vehicle.getOwner();
            mailService.sendInspectionReminderEmail(owner, vehicle, insurance, diff);
        }
    }

    @Override
    public void sendRoutineServiceReminders(Set<LocalDate> dates, LocalDate now) {
        List<RoutineService> services = routineServiceRepository.findByNextByDateIn(dates);
        for (RoutineService service : services) {
            int diff = (int) ChronoUnit.DAYS.between(now, service.getNextByDate());
            Vehicle vehicle = service.getVehicle();
            User owner = vehicle.getOwner();
            mailService.sendRoutineServiceReminderEmail(owner, vehicle, service, diff);
        }
    }
}
