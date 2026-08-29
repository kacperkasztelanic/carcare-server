package com.kasztelanic.carcare.golden;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kasztelanic.carcare.domain.Inspection;
import com.kasztelanic.carcare.domain.Insurance;
import com.kasztelanic.carcare.domain.RoutineService;
import com.kasztelanic.carcare.domain.User;
import com.kasztelanic.carcare.domain.Vehicle;
import com.kasztelanic.carcare.fixtures.SessionFixtures;
import com.kasztelanic.carcare.service.MailService;
import com.kasztelanic.carcare.service.ReminderService;
import com.kasztelanic.carcare.web.rest.AbstractSessionIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifies reminder selection and typed mail dispatch against the captured baseline. */
@Import(ReminderSelectionParityIT.FixedClockConfiguration.class)
class ReminderSelectionParityIT extends AbstractSessionIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();
    private static final Clock FIXED_CLOCK = Clock.fixed(
        SessionFixtures.GOLDEN_REFERENCE_DATE.atStartOfDay(SYSTEM_ZONE).toInstant(), SYSTEM_ZONE);

    @Autowired
    private ReminderService reminderService;


    @MockBean
    private MailService mailService;

    @BeforeEach
    void resetReminderCollaborators() {
        reset(mailService);
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return FIXED_CLOCK;
        }
    }

    @Test
    void typedReminderSeamMatchesGoldenCalls() throws Exception {
        Map<String, Long> ids = sessionFixtures.seedGoldenDataset();
        LocalDate referenceDate = SessionFixtures.GOLDEN_REFERENCE_DATE;
        Set<LocalDate> dates = Set.of(referenceDate.plusDays(3), referenceDate.plusDays(7));

        reminderService.sendInsuranceReminders(dates, referenceDate);
        reminderService.sendInspectionReminders(dates, referenceDate);
        reminderService.sendRoutineServiceReminders(dates, referenceDate);

        assertReminderCallsMatch("golden/reminders/typed-seam.json", ids);
    }

    @Test
    void fullReminderResourceMatchesGoldenCalls() throws Exception {
        Map<String, Long> ids = sessionFixtures.seedGoldenDataset();

        mockMvc.perform(get("/api/reminder/send")
            .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(content().string(""));

        assertReminderCallsMatch("golden/reminders/full-path.json", ids);
    }

    @Test
    void userCannotDispatchReminders() throws Exception {
        mockMvc.perform(get("/api/reminder/send")
            .with(SecurityMockMvcRequestPostProcessors.user("user").roles("USER")))
            .andExpect(status().isForbidden());

        verifyNoInteractions(mailService);
    }

    @Test
    void anonymousCannotDispatchReminders() throws Exception {
        mockMvc.perform(get("/api/reminder/send"))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(mailService);
    }

    @Test
    void archivedDueEventsAreNotSelected() {
        LocalDate referenceDate = SessionFixtures.GOLDEN_REFERENCE_DATE;
        LocalDate dueDate = referenceDate.plusDays(3);
        Vehicle active = sessionFixtures.vehicleFor("user");
        Vehicle archived = sessionFixtures.vehicleFor("user");

        Insurance activeInsurance = sessionFixtures.insuranceFor(active, 1_000, referenceDate,
            referenceDate.minusYears(1), dueDate, 10_000);
        Inspection activeInspection = sessionFixtures.inspectionFor(active, 1_000, referenceDate, 10_000, dueDate);
        RoutineService activeService = sessionFixtures.routineServiceFor(active, 1_000, referenceDate, 10_000,
            2_000, dueDate);
        sessionFixtures.insuranceFor(archived, 1_000, referenceDate, referenceDate.minusYears(1), dueDate, 10_000);
        sessionFixtures.inspectionFor(archived, 1_000, referenceDate, 10_000, dueDate);
        sessionFixtures.routineServiceFor(archived, 1_000, referenceDate, 10_000, 2_000, dueDate);
        sessionFixtures.archive(archived, Instant.parse("2026-04-01T00:00:00Z"));

        Set<LocalDate> dates = Set.of(dueDate);
        reminderService.sendInsuranceReminders(dates, referenceDate);
        reminderService.sendInspectionReminders(dates, referenceDate);
        reminderService.sendRoutineServiceReminders(dates, referenceDate);

        verify(mailService).sendInsuranceReminderEmail(eq(active.getOwner()), eq(active), eq(activeInsurance), eq(3));
        verify(mailService).sendInspectionReminderEmail(eq(active.getOwner()), eq(active), eq(activeInspection), eq(3));
        verify(mailService).sendRoutineServiceReminderEmail(eq(active.getOwner()), eq(active), eq(activeService), eq(3));
        verifyNoMoreInteractions(mailService);
    }

    private void assertReminderCallsMatch(String resourceName, Map<String, Long> ids) throws IOException {
        List<ReminderEntry> expected = loadExpected(resourceName);
        List<ReminderEntry> actual = captureReminderCalls(ids);

        assertThat(expected).hasSize(6);
        assertThat(actual).hasSize(6);
        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
        verifyNoMoreInteractions(mailService);
    }

    private List<ReminderEntry> loadExpected(String resourceName) throws IOException {
        InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName);
        if (stream == null) {
            throw new IllegalArgumentException("Golden reminder reference not found: " + resourceName);
        }
        try (InputStream input = stream) {
            JsonNode root = MAPPER.readTree(input);
            assertThat(root.path("referenceDate").asText()).isEqualTo(SessionFixtures.GOLDEN_REFERENCE_DATE.toString());
            assertThat(readDates(root.path("dates"))).containsExactly(
                SessionFixtures.GOLDEN_REFERENCE_DATE.plusDays(3),
                SessionFixtures.GOLDEN_REFERENCE_DATE.plusDays(7));
            assertThat(readIntegers(root.path("configuredAdvances"))).containsExactly(3, 7);

            List<ReminderEntry> entries = new ArrayList<>();
            for (JsonNode node : root.path("entries")) {
                entries.add(new ReminderEntry(
                    node.path("eventType").asText(),
                    node.path("ownerLogin").asText(),
                    node.path("ownerLangKey").asText(),
                    node.path("vehicleHandle").asText(),
                    node.path("eventHandle").asText(),
                    LocalDate.parse(node.path("dueDate").asText()),
                    node.path("diff").asInt()));
            }
            return entries;
        }
    }

    private List<LocalDate> readDates(JsonNode values) {
        List<LocalDate> dates = new ArrayList<>();
        values.forEach(value -> dates.add(LocalDate.parse(value.asText())));
        return dates;
    }

    private List<Integer> readIntegers(JsonNode values) {
        List<Integer> integers = new ArrayList<>();
        values.forEach(value -> integers.add(value.asInt()));
        return integers;
    }

    private List<ReminderEntry> captureReminderCalls(Map<String, Long> ids) {
        Map<Long, String> vehicleHandleById = handles(ids, "vehicle:");
        Map<Long, String> insuranceHandleById = handles(ids, "insurance:");
        Map<Long, String> inspectionHandleById = handles(ids, "inspection:");
        Map<Long, String> routineServiceHandleById = handles(ids, "routine-service:");

        var insuranceUsers = org.mockito.ArgumentCaptor.forClass(User.class);
        var insuranceVehicles = org.mockito.ArgumentCaptor.forClass(Vehicle.class);
        var insurances = org.mockito.ArgumentCaptor.forClass(Insurance.class);
        var insuranceDiffs = org.mockito.ArgumentCaptor.forClass(Integer.class);
        verify(mailService, times(2)).sendInsuranceReminderEmail(
            insuranceUsers.capture(), insuranceVehicles.capture(), insurances.capture(), insuranceDiffs.capture());

        var inspectionUsers = org.mockito.ArgumentCaptor.forClass(User.class);
        var inspectionVehicles = org.mockito.ArgumentCaptor.forClass(Vehicle.class);
        var inspections = org.mockito.ArgumentCaptor.forClass(Inspection.class);
        var inspectionDiffs = org.mockito.ArgumentCaptor.forClass(Integer.class);
        verify(mailService, times(2)).sendInspectionReminderEmail(
            inspectionUsers.capture(), inspectionVehicles.capture(), inspections.capture(), inspectionDiffs.capture());

        var serviceUsers = org.mockito.ArgumentCaptor.forClass(User.class);
        var serviceVehicles = org.mockito.ArgumentCaptor.forClass(Vehicle.class);
        var services = org.mockito.ArgumentCaptor.forClass(RoutineService.class);
        var serviceDiffs = org.mockito.ArgumentCaptor.forClass(Integer.class);
        verify(mailService, times(2)).sendRoutineServiceReminderEmail(
            serviceUsers.capture(), serviceVehicles.capture(), services.capture(), serviceDiffs.capture());

        List<ReminderEntry> entries = new ArrayList<>();
        for (int i = 0; i < insuranceUsers.getAllValues().size(); i++) {
            Insurance insurance = insurances.getAllValues().get(i);
            entries.add(entry("INSURANCE", insuranceUsers.getAllValues().get(i), insuranceVehicles.getAllValues().get(i),
                vehicleHandleById, insuranceHandleById, insurance.getId(), insurance.getValidThru(),
                insuranceDiffs.getAllValues().get(i)));
        }
        for (int i = 0; i < inspectionUsers.getAllValues().size(); i++) {
            Inspection inspection = inspections.getAllValues().get(i);
            entries.add(entry("INSPECTION", inspectionUsers.getAllValues().get(i), inspectionVehicles.getAllValues().get(i),
                vehicleHandleById, inspectionHandleById, inspection.getId(), inspection.getValidThru(),
                inspectionDiffs.getAllValues().get(i)));
        }
        for (int i = 0; i < serviceUsers.getAllValues().size(); i++) {
            RoutineService service = services.getAllValues().get(i);
            entries.add(entry("ROUTINE_SERVICE", serviceUsers.getAllValues().get(i), serviceVehicles.getAllValues().get(i),
                vehicleHandleById, routineServiceHandleById, service.getId(), service.getNextByDate(),
                serviceDiffs.getAllValues().get(i)));
        }
        return entries;
    }

    private Map<Long, String> handles(Map<String, Long> ids, String prefix) {
        Map<Long, String> handles = new LinkedHashMap<>();
        ids.forEach((handle, id) -> {
            if (handle.startsWith(prefix)) {
                handles.put(id, handle);
            }
        });
        return handles;
    }

    private ReminderEntry entry(String eventType, User owner, Vehicle vehicle, Map<Long, String> vehicleHandleById,
                                Map<Long, String> eventHandleById, Long eventId, LocalDate dueDate, int diff) {
        return new ReminderEntry(eventType, owner.getLogin(), owner.getLangKey(),
            handle(vehicleHandleById, vehicle.getId()), handle(eventHandleById, eventId), dueDate, diff);
    }

    private String handle(Map<Long, String> handleById, Long id) {
        String handle = handleById.get(id);
        if (handle == null) {
            throw new AssertionError("No golden handle for generated id " + id);
        }
        return handle;
    }

    private record ReminderEntry(String eventType, String ownerLogin, String ownerLangKey, String vehicleHandle,
                                 String eventHandle, LocalDate dueDate, int diff) {
    }
}
