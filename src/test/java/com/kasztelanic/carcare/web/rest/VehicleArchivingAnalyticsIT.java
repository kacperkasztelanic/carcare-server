package com.kasztelanic.carcare.web.rest;

import com.kasztelanic.carcare.domain.Vehicle;
import com.kasztelanic.carcare.service.dto.CostRequest;
import com.kasztelanic.carcare.service.dto.PeriodVehicle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VehicleArchivingAnalyticsIT extends AbstractSessionIT {

    private static final LocalDate DATE_FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate DATE_TO = LocalDate.of(2026, 3, 31);


    @Test
    void costConsumersAppendMatchingArchivedVehiclesOnceInIdOrder() throws Exception {
        Vehicle activeFirst = sessionFixtures.vehicleFor("user");
        sessionFixtures.refuelFor(activeFirst, 1_000, DATE_FROM, 1_000, 100);
        Vehicle activeSecond = sessionFixtures.vehicleFor("user");
        sessionFixtures.repairFor(activeSecond, 1_100, DATE_TO, 200);

        Vehicle archivedRefuel = archivedFixture(sessionFixtures.vehicleFor("user"));
        sessionFixtures.refuelFor(archivedRefuel, 1_200, DATE_FROM, 1_000, 300);
        Vehicle archivedRepair = archivedFixture(sessionFixtures.vehicleFor("user"));
        sessionFixtures.repairFor(archivedRepair, 1_300, DATE_TO, 400);
        Vehicle archivedRoutineService = archivedFixture(sessionFixtures.vehicleFor("user"));
        sessionFixtures.routineServiceFor(archivedRoutineService, 1_400, DATE_FROM, 500,
            2_000, LocalDate.of(2027, 1, 1));
        Vehicle archivedInspection = archivedFixture(sessionFixtures.vehicleFor("user"));
        sessionFixtures.inspectionFor(archivedInspection, 1_500, DATE_TO, 600, LocalDate.of(2027, 3, 31));
        Vehicle archivedInsurance = archivedFixture(sessionFixtures.vehicleFor("user"));
        sessionFixtures.insuranceFor(archivedInsurance, 1_600, DATE_FROM, DATE_FROM,
            DATE_TO, 700);

        Vehicle outsidePeriod = archivedFixture(sessionFixtures.vehicleFor("user"));
        sessionFixtures.refuelFor(outsidePeriod, 1_700, DATE_FROM.minusDays(1), 1_000, 800);
        Vehicle foreign = archivedFixture(sessionFixtures.vehicleFor("admin"));
        sessionFixtures.refuelFor(foreign, 1_800, DATE_FROM, 1_000, 900);

        CostRequest request = CostRequest.of(
            List.of(activeFirst.getId(), activeSecond.getId(), archivedRefuel.getId()), DATE_FROM, DATE_TO);

        mockMvc.perform(post("/api/stats/cost")
                .with(user("user"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(7)))
            .andExpect(jsonPath("$[0].periodVehicle.vehicleId").value(activeFirst.getId()))
            .andExpect(jsonPath("$[0].refuelCosts").value(1.0))
            .andExpect(jsonPath("$[1].periodVehicle.vehicleId").value(activeSecond.getId()))
            .andExpect(jsonPath("$[1].repairCosts").value(2.0))
            .andExpect(jsonPath("$[2].periodVehicle.vehicleId").value(archivedRefuel.getId()))
            .andExpect(jsonPath("$[2].refuelCosts").value(3.0))
            .andExpect(jsonPath("$[3].periodVehicle.vehicleId").value(archivedRepair.getId()))
            .andExpect(jsonPath("$[3].repairCosts").value(4.0))
            .andExpect(jsonPath("$[4].periodVehicle.vehicleId").value(archivedRoutineService.getId()))
            .andExpect(jsonPath("$[4].routineServiceCosts").value(5.0))
            .andExpect(jsonPath("$[5].periodVehicle.vehicleId").value(archivedInspection.getId()))
            .andExpect(jsonPath("$[5].inspectionCosts").value(6.0))
            .andExpect(jsonPath("$[6].periodVehicle.vehicleId").value(archivedInsurance.getId()))
            .andExpect(jsonPath("$[6].insuranceCosts").value(7.0));

        List<String> reportCells = stringCells(mockMvc.perform(post("/api/reports/costs")
                .with(user("user"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/vnd.ms-excel"))
            .andReturn().getResponse().getContentAsByteArray());
        assertThat(reportCells).anyMatch(value -> value.contains(activeFirst.getLicensePlate()));
        assertThat(reportCells).anyMatch(value -> value.contains(activeSecond.getLicensePlate()));
        assertThat(reportCells).anyMatch(value -> value.contains(archivedRefuel.getLicensePlate()));
        assertThat(reportCells).anyMatch(value -> value.contains(archivedRepair.getLicensePlate()));
        assertThat(reportCells).anyMatch(value -> value.contains(archivedRoutineService.getLicensePlate()));
        assertThat(reportCells).anyMatch(value -> value.contains(archivedInspection.getLicensePlate()));
        assertThat(reportCells).anyMatch(value -> value.contains(archivedInsurance.getLicensePlate()));
        assertThat(reportCells).noneMatch(value -> value.contains(outsidePeriod.getLicensePlate()));
        assertThat(reportCells).noneMatch(value -> value.contains(foreign.getLicensePlate()));
    }

    @Test
    void ownedArchivedVehicleRemainsAvailableToHistoricalReportsAndStatistics() throws Exception {
        Vehicle archived = sessionFixtures.vehicleFor("user");
        sessionFixtures.refuelFor(archived, 1_000, DATE_FROM, 10_000, 1_000);
        sessionFixtures.refuelFor(archived, 1_500, DATE_TO, 10_000, 1_000);
        archivedFixture(archived);
        PeriodVehicle periodVehicle = PeriodVehicle.of(archived.getId(), DATE_FROM, DATE_TO);

        mockMvc.perform(get("/api/reports/vehicle/{id}", archived.getId()).with(user("user")))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/vnd.ms-excel"));

        mockMvc.perform(post("/api/stats/mileage")
                .with(user("user"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(periodVehicle)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mileageByDate['2026-01-01']").value(1_000))
            .andExpect(jsonPath("$.mileageByDate['2026-03-31']").value(1_500));

        mockMvc.perform(post("/api/stats/consumption/per-period")
                .with(user("user"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(periodVehicle)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.volume").value(10.0))
            .andExpect(jsonPath("$.mileage").value(500.0))
            .andExpect(jsonPath("$.averageConsumption").value(2.0));

        mockMvc.perform(post("/api/stats/consumption/per-refuel")
                .with(user("user"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(periodVehicle)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].volume").value(10.0))
            .andExpect(jsonPath("$[0].mileage").value(500.0));
    }

    @Test
    void archivedAnalyticsRemainOwnerScoped() throws Exception {
        Vehicle archived = sessionFixtures.vehicleFor("user");
        sessionFixtures.refuelFor(archived, 1_000, DATE_FROM, 1_000, 100);
        archivedFixture(archived);
        PeriodVehicle periodVehicle = PeriodVehicle.of(archived.getId(), DATE_FROM, DATE_TO);
        CostRequest costRequest = CostRequest.of(List.of(archived.getId()), DATE_FROM, DATE_TO);

        mockMvc.perform(post("/api/stats/cost")
                .with(user("admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(costRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(post("/api/stats/mileage")
                .with(user("admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(periodVehicle)))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/reports/vehicle/{id}", archived.getId()).with(user("admin")))
            .andExpect(status().isNotFound());
    }

    private Vehicle archivedFixture(Vehicle vehicle) {
        return sessionFixtures.archive(vehicle, Instant.parse("2026-08-28T10:00:00Z"));
    }

    private static List<String> stringCells(byte[] workbookBytes) throws Exception {
        List<String> values = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(workbookBytes))) {
            for (Sheet sheet : workbook) {
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        if (cell.getCellType() == CellType.STRING) {
                            values.add(cell.getStringCellValue());
                        }
                    }
                }
            }
        }
        return values;
    }
}
