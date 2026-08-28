package com.kasztelanic.carcare.golden;

import com.kasztelanic.carcare.service.dto.CostRequest;
import com.kasztelanic.carcare.service.dto.PeriodVehicle;
import com.kasztelanic.carcare.web.rest.AbstractSessionIT;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.transaction.AfterTransaction;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Permanent value-level parity checks for the captured report and statistics responses. */
class ReportParityIT extends AbstractSessionIT {

    private static final LocalDate DATE_FROM = LocalDate.of(2026, 3, 1);
    private static final LocalDate DATE_TO = LocalDate.of(2026, 3, 31);

    @Test
    void vehicleReportMatchesEnglishGolden() throws Exception {
        Map<String, Long> ids = sessionFixtures.seedGoldenDataset();

        GoldenReference.load("golden/reports/vehicle-en.json")
            .assertWorkbookMatches(mockMvc.perform(get("/api/reports/vehicle/{id}", ids.get("vehicle:en-primary"))
                .with(user("admin"))).andReturn(), ids);
    }

    @Test
    void vehicleReportMatchesPolishGolden() throws Exception {
        Map<String, Long> ids = sessionFixtures.seedGoldenDataset();

        GoldenReference.load("golden/reports/vehicle-pl.json")
            .assertWorkbookMatches(mockMvc.perform(get("/api/reports/vehicle/{id}", ids.get("vehicle:pl-primary"))
                .with(user("user"))).andReturn(), ids);
    }

    @Test
    void costReportMatchesEnglishGolden() throws Exception {
        Map<String, Long> ids = sessionFixtures.seedGoldenDataset();

        GoldenReference.load("golden/reports/costs-en.json")
            .assertWorkbookMatches(mockMvc.perform(post("/api/reports/costs")
                .with(user("admin"))
                .contentType(APPLICATION_JSON)
                .content(json(costRequest(ids)))).andReturn(), ids);
    }

    @Test
    void unownedVehicleReportMatchesGolden() throws Exception {
        Map<String, Long> ids = sessionFixtures.seedGoldenDataset();

        GoldenReference.load("golden/reports/vehicle-unowned.json")
            .assertWorkbookMatches(mockMvc.perform(get("/api/reports/vehicle/{id}", ids.get("vehicle:pl-primary"))
                .with(user("admin"))).andReturn(), ids);
    }

    @Test
    void consumptionPerPeriodMatchesEnglishGolden() throws Exception {
        Map<String, Long> ids = sessionFixtures.seedGoldenDataset();

        GoldenReference.load("golden/stats/consumption-period-en.json")
            .assertJsonMatches(mockMvc.perform(post("/api/stats/consumption/per-period")
                .with(user("admin"))
                .contentType(APPLICATION_JSON)
                .content(json(periodVehicle(ids, "vehicle:en-primary")))).andReturn(), ids);
    }

    @Test
    void zeroConsumptionPeriodRecordsBaselineFailureAndCurrentFix() throws Exception {
        Map<String, Long> ids = sessionFixtures.seedGoldenDataset();
        GoldenReference reference = GoldenReference.load("golden/stats/consumption-period-zero.json");

        // The baseline's 500 came from AverageConsumptionResult.java:19-26 and was fixed in
        // 4ad88bd. The 0.0 result deliberately conflates unknown with real zero; S-07 owns that
        // client-visible contract discussion.
        assertThat(reference.status()).isEqualTo(500);
        mockMvc.perform(post("/api/stats/consumption/per-period")
                .with(user("admin"))
                .contentType(APPLICATION_JSON)
                .content(json(periodVehicle(ids, "vehicle:zero-consumption"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.volume").value(0.0))
            .andExpect(jsonPath("$.mileage").value(0))
            .andExpect(jsonPath("$.averageConsumption").value(0.0));
    }

    @Test
    void consumptionPerRefuelMatchesEnglishGolden() throws Exception {
        Map<String, Long> ids = sessionFixtures.seedGoldenDataset();

        GoldenReference.load("golden/stats/consumption-refuel-en.json")
            .assertJsonMatches(mockMvc.perform(post("/api/stats/consumption/per-refuel")
                .with(user("admin"))
                .contentType(APPLICATION_JSON)
                .content(json(periodVehicle(ids, "vehicle:en-primary")))).andReturn(), ids);
    }

    @Test
    void zeroConsumptionPerRefuelMatchesGolden() throws Exception {
        Map<String, Long> ids = sessionFixtures.seedGoldenDataset();

        GoldenReference.load("golden/stats/consumption-refuel-zero.json")
            .assertJsonMatches(mockMvc.perform(post("/api/stats/consumption/per-refuel")
                .with(user("admin"))
                .contentType(APPLICATION_JSON)
                .content(json(periodVehicle(ids, "vehicle:zero-consumption")))).andReturn(), ids);
    }

    @Test
    void mileageMatchesEnglishGolden() throws Exception {
        Map<String, Long> ids = sessionFixtures.seedGoldenDataset();

        GoldenReference.load("golden/stats/mileage-en.json")
            .assertJsonMatches(mockMvc.perform(post("/api/stats/mileage")
                .with(user("admin"))
                .contentType(APPLICATION_JSON)
                .content(json(periodVehicle(ids, "vehicle:en-primary")))).andReturn(), ids);
    }

    @Test
    void unownedMileageMatchesGolden() throws Exception {
        Map<String, Long> ids = sessionFixtures.seedGoldenDataset();

        GoldenReference.load("golden/stats/mileage-unowned.json")
            .assertJsonMatches(mockMvc.perform(post("/api/stats/mileage")
                .with(user("admin"))
                .contentType(APPLICATION_JSON)
                .content(json(periodVehicle(ids, "vehicle:pl-primary")))).andReturn(), ids);
    }

    @Test
    void costStatisticsMatchesEnglishGolden() throws Exception {
        Map<String, Long> ids = sessionFixtures.seedGoldenDataset();

        GoldenReference.load("golden/stats/cost-en.json")
            .assertJsonMatches(mockMvc.perform(post("/api/stats/cost")
                .with(user("admin"))
                .contentType(APPLICATION_JSON)
                .content(json(costRequest(ids)))).andReturn(), ids);
    }

    @Test
    void forthcomingEventsKeepTheCapturedOrdering() throws Exception {
        Map<String, Long> ids = sessionFixtures.seedGoldenDataset();
        // routine-service:null-next-date stays in the fixture: it is the shape that used to 500
        // this endpoint, and it must now be filtered out rather than sorted.

        mockMvc.perform(post("/api/events")
                .with(user("admin"))
                .contentType(APPLICATION_JSON)
                .content(json(List.of(
                    PeriodVehicle.of(ids.get("vehicle:en-primary"), LocalDate.of(2026, 4, 15), LocalDate.of(2026, 4, 22)),
                    PeriodVehicle.of(ids.get("vehicle:pl-primary"), LocalDate.of(2026, 4, 15), LocalDate.of(2026, 4, 22)),
                    PeriodVehicle.of(ids.get("vehicle:zero-consumption"), LocalDate.of(2026, 4, 15), LocalDate.of(2026, 4, 22))
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(4)))
            .andExpect(jsonPath("$[0].eventType").value("INSPECTION"))
            .andExpect(jsonPath("$[0].dateThru").value("2026-04-18"))
            .andExpect(jsonPath("$[1].eventType").value("INSURANCE"))
            .andExpect(jsonPath("$[1].dateThru").value("2026-04-18"))
            .andExpect(jsonPath("$[2].eventType").value("SERVICE"))
            .andExpect(jsonPath("$[2].dateThru").value("2026-04-18"))
            .andExpect(jsonPath("$[3].eventType").value("INSURANCE"))
            .andExpect(jsonPath("$[3].dateThru").value("2026-04-19"));
    }

    @AfterTransaction
    void evictGoldenOwnerCachesAfterRollback() {
        sessionFixtures.evictGoldenOwnerCaches();
    }

    private static PeriodVehicle periodVehicle(Map<String, Long> ids, String vehicleHandle) {
        return PeriodVehicle.of(ids.get(vehicleHandle), DATE_FROM, DATE_TO);
    }

    private static CostRequest costRequest(Map<String, Long> ids) {
        return CostRequest.of(List.of(
            ids.get("vehicle:en-primary"),
            ids.get("vehicle:pl-primary"),
            ids.get("vehicle:zero-consumption")
        ), DATE_FROM, DATE_TO);
    }
}
