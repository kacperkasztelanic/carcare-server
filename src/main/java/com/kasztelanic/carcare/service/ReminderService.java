package com.kasztelanic.carcare.service;

import java.time.LocalDate;
import java.util.Set;

public interface ReminderService {

    void sendReminders();

    void sendInsuranceReminders(Set<LocalDate> dates, LocalDate now);

    void sendInspectionReminders(Set<LocalDate> dates, LocalDate now);

    void sendRoutineServiceReminders(Set<LocalDate> dates, LocalDate now);

}