package com.kasztelanic.carcare.web.rest;

import com.kasztelanic.carcare.domain.Inspection;
import com.kasztelanic.carcare.domain.Insurance;
import com.kasztelanic.carcare.domain.Refuel;
import com.kasztelanic.carcare.domain.Repair;
import com.kasztelanic.carcare.domain.RoutineService;
import com.kasztelanic.carcare.domain.Vehicle;
import com.kasztelanic.carcare.fixtures.SessionFixtures;
import com.kasztelanic.carcare.service.dto.CostRequest;
import com.kasztelanic.carcare.service.dto.FuelTypeDto;
import com.kasztelanic.carcare.service.dto.InspectionDto;
import com.kasztelanic.carcare.service.dto.InsuranceDto;
import com.kasztelanic.carcare.service.dto.InsuranceTypeDto;
import com.kasztelanic.carcare.service.dto.PeriodVehicle;
import com.kasztelanic.carcare.service.dto.RefuelDto;
import com.kasztelanic.carcare.service.dto.RepairDto;
import com.kasztelanic.carcare.service.dto.RoutineServiceDto;
import com.kasztelanic.carcare.service.dto.VehicleDetailsDto;
import com.kasztelanic.carcare.service.dto.VehicleDto;
import com.kasztelanic.carcare.service.dto.VehicleEventDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cross-user guarantee for every session-parity resource and shared read path.
 */
class OwnerIsolationIT extends AbstractSessionIT {

    private static final String OWNER = "user";
    private static final String OTHER_OWNER = "admin";
    private static final LocalDate DATE_FROM = LocalDate.of(2024, 1, 1);
    private static final LocalDate DATE_TO = LocalDate.of(2025, 12, 31);

    @Test
    void vehicleRowsAreInvisibleAndCreatedVehiclesRemainOwnedByTheCaller() throws Exception {
        Vehicle foreignVehicle = sessionFixtures.vehicleFor(OWNER);

        mockMvc.perform(get("/api/vehicle/{id}", foreignVehicle.getId()).with(user(OTHER_OWNER)))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/vehicle/all").with(user(OTHER_OWNER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(put("/api/vehicle/{id}", foreignVehicle.getId()).with(user(OTHER_OWNER))
                .contentType(MediaType.APPLICATION_JSON).content(json(vehicleRequest("Foreign update"))))
            .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/vehicle/{id}", foreignVehicle.getId()).with(user(OTHER_OWNER)))
            .andExpect(status().isNotFound());

        long createdVehicleId = responseId(mockMvc.perform(post("/api/vehicle").with(user(OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"make":"Caller make","model":"Caller model","licensePlate":"FX-CALLER",
                    "fuelType":{"type":"fixture-fuel","description":"Fixture fuel"},
                    "vehicleDetails":{"modelSuffix":"","vinNumber":"","vehicleCard":"",
                    "registrationCertificate":"","yearOfManufacture":0,"engineVolume":0,"enginePower":0,
                    "weight":0,"notes":""},"owner":{"login":"admin"}}
                    """))
            .andExpect(status().isCreated())
            .andReturn());

        mockMvc.perform(get("/api/vehicle/{id}", createdVehicleId).with(user(OWNER)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/vehicle/{id}", createdVehicleId).with(user(OTHER_OWNER)))
            .andExpect(status().isNotFound());
    }

    @Test
    void refuelRowsAndForeignParentAreInvisible() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleFor(OWNER);
        Refuel refuel = sessionFixtures.refuelFor(vehicle);

        mockMvc.perform(get("/api/refuel/{id}", refuel.getId()).with(user(OTHER_OWNER))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/refuel/all/{vehicleId}", vehicle.getId()).with(user(OTHER_OWNER)))
            .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(put("/api/refuel/{id}", refuel.getId()).with(user(OTHER_OWNER))
                .contentType(MediaType.APPLICATION_JSON).content(json(refuelRequest())))
            .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/refuel/{id}", refuel.getId()).with(user(OTHER_OWNER))).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/refuel/{vehicleId}", vehicle.getId()).with(user(OTHER_OWNER))
                .contentType(MediaType.APPLICATION_JSON).content(json(refuelRequest())))
            .andExpect(status().isNotFound());
    }

    @Test
    void repairRowsAndForeignParentAreInvisible() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleFor(OWNER);
        Repair repair = sessionFixtures.repairFor(vehicle);

        mockMvc.perform(get("/api/repair/{id}", repair.getId()).with(user(OTHER_OWNER))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/repair/all/{vehicleId}", vehicle.getId()).with(user(OTHER_OWNER)))
            .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(put("/api/repair/{id}", repair.getId()).with(user(OTHER_OWNER))
                .contentType(MediaType.APPLICATION_JSON).content(json(repairRequest())))
            .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/repair/{id}", repair.getId()).with(user(OTHER_OWNER))).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/repair/{vehicleId}", vehicle.getId()).with(user(OTHER_OWNER))
                .contentType(MediaType.APPLICATION_JSON).content(json(repairRequest())))
            .andExpect(status().isNotFound());
    }

    @Test
    void routineServiceRowsAndForeignParentAreInvisible() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleFor(OWNER);
        RoutineService routineService = sessionFixtures.routineServiceFor(vehicle);

        mockMvc.perform(get("/api/routine-service/{id}", routineService.getId()).with(user(OTHER_OWNER)))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/routine-service/all/{vehicleId}", vehicle.getId()).with(user(OTHER_OWNER)))
            .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(put("/api/routine-service/{id}", routineService.getId()).with(user(OTHER_OWNER))
                .contentType(MediaType.APPLICATION_JSON).content(json(routineServiceRequest())))
            .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/routine-service/{id}", routineService.getId()).with(user(OTHER_OWNER)))
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/routine-service/{vehicleId}", vehicle.getId()).with(user(OTHER_OWNER))
                .contentType(MediaType.APPLICATION_JSON).content(json(routineServiceRequest())))
            .andExpect(status().isNotFound());
    }

    @Test
    void inspectionRowsAndForeignParentAreInvisible() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleFor(OWNER);
        Inspection inspection = sessionFixtures.inspectionFor(vehicle);

        mockMvc.perform(get("/api/inspection/{id}", inspection.getId()).with(user(OTHER_OWNER)))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/inspection/all/{vehicleId}", vehicle.getId()).with(user(OTHER_OWNER)))
            .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(put("/api/inspection/{id}", inspection.getId()).with(user(OTHER_OWNER))
                .contentType(MediaType.APPLICATION_JSON).content(json(inspectionRequest())))
            .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/inspection/{id}", inspection.getId()).with(user(OTHER_OWNER)))
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/inspection/{vehicleId}", vehicle.getId()).with(user(OTHER_OWNER))
                .contentType(MediaType.APPLICATION_JSON).content(json(inspectionRequest())))
            .andExpect(status().isNotFound());
    }

    @Test
    void insuranceRowsAndForeignParentAreInvisible() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleFor(OWNER);
        Insurance insurance = sessionFixtures.insuranceFor(vehicle);

        mockMvc.perform(get("/api/insurance/{id}", insurance.getId()).with(user(OTHER_OWNER)))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/insurance/all/{vehicleId}", vehicle.getId()).with(user(OTHER_OWNER)))
            .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(put("/api/insurance/{id}", insurance.getId()).with(user(OTHER_OWNER))
                .contentType(MediaType.APPLICATION_JSON).content(json(insuranceRequest())))
            .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/insurance/{id}", insurance.getId()).with(user(OTHER_OWNER)))
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/insurance/{vehicleId}", vehicle.getId()).with(user(OTHER_OWNER))
                .contentType(MediaType.APPLICATION_JSON).content(json(insuranceRequest())))
            .andExpect(status().isNotFound());
    }

    @Test
    void sharedReadPathsUseOnlyTheCurrentOwnersVehicles() throws Exception {
        Vehicle vehicle = sessionFixtures.vehicleWithEventsFor(OWNER);
        sessionFixtures.refuelFor(vehicle, 11_000, LocalDate.of(2024, 2, 10));
        PeriodVehicle periodVehicle = PeriodVehicle.of(vehicle.getId(), DATE_FROM, DATE_TO);
        CostRequest costRequest = CostRequest.of(List.of(vehicle.getId()), DATE_FROM, DATE_TO);

        assertOwnerAndForeignResult("/api/stats/consumption/per-period", periodVehicle, ForeignResult.EMPTY_RESULT_500);
        assertOwnerAndForeignResult("/api/stats/consumption/per-refuel", periodVehicle, ForeignResult.EMPTY_LIST);
        assertOwnerAndForeignResult("/api/stats/mileage", periodVehicle, ForeignResult.NOT_FOUND);
        assertOwnerAndForeignResult("/api/stats/cost", costRequest, ForeignResult.EMPTY_LIST);
        assertOwnerAndForeignResult("/api/events", List.of(periodVehicle), ForeignResult.EMPTY_LIST);
        assertOwnerAndForeignResult("/api/reports/costs", costRequest, ForeignResult.EMPTY_REPORT);

        mockMvc.perform(get("/api/reports/vehicle/{id}", vehicle.getId()).with(user(OWNER)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/reports/vehicle/{id}", vehicle.getId()).with(user(OTHER_OWNER)))
            .andExpect(status().isNotFound());
    }

    private void assertOwnerAndForeignResult(String path, Object request, ForeignResult foreignResult) throws Exception {
        mockMvc.perform(post(path).with(user(OWNER)).contentType(MediaType.APPLICATION_JSON).content(json(request)))
            .andExpect(status().isOk());
        switch (foreignResult) {
            case EMPTY_LIST -> mockMvc.perform(post(path).with(user(OTHER_OWNER))
                    .contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
            case NOT_FOUND -> mockMvc.perform(post(path).with(user(OTHER_OWNER))
                    .contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isNotFound());
            case EMPTY_REPORT -> mockMvc.perform(post(path).with(user(OTHER_OWNER))
                    .contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isOk());
            // The empty result reaches AverageConsumptionResult#getAverageConsumption and divides 0 by 0.
            // Characterized by Phase 5 instead of changing production behavior outside the approved scope.
            case EMPTY_RESULT_500 -> mockMvc.perform(post(path).with(user(OTHER_OWNER))
                    .contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isInternalServerError());
        }
    }

    private enum ForeignResult {
        EMPTY_LIST,
        NOT_FOUND,
        EMPTY_REPORT,
        EMPTY_RESULT_500
    }

    private static VehicleDto vehicleRequest(String make) {
        return VehicleDto.builder()
            .make(make)
            .model("Fixture model")
            .licensePlate("FX-POST")
            .fuelType(FuelTypeDto.of(SessionFixtures.DEFAULT_FUEL_TYPE, "Fixture fuel"))
            .vehicleDetails(VehicleDetailsDto.defaultBuilder().build())
            .build();
    }

    private static RefuelDto refuelRequest() {
        return RefuelDto.builder().vehicleEvent(VehicleEventDto.of(12_000, LocalDate.of(2024, 6, 1)))
            .costInCents(10_000).volume(42_000).station("Fixture station").build();
    }

    private static RepairDto repairRequest() {
        return RepairDto.builder().vehicleEvent(VehicleEventDto.of(12_000, LocalDate.of(2024, 6, 1)))
            .costInCents(10_000).station("Fixture station").details("Fixture repair").build();
    }

    private static RoutineServiceDto routineServiceRequest() {
        return RoutineServiceDto.builder().vehicleEvent(VehicleEventDto.of(12_000, LocalDate.of(2024, 6, 1)))
            .costInCents(10_000).nextByMileage(20_000).nextByDate(LocalDate.of(2025, 6, 1))
            .station("Fixture station").details("Fixture routine service").build();
    }

    private static InspectionDto inspectionRequest() {
        return InspectionDto.builder().vehicleEvent(VehicleEventDto.of(12_000, LocalDate.of(2024, 6, 1)))
            .costInCents(10_000).station("Fixture station").validThru(LocalDate.of(2025, 6, 1))
            .details("Fixture inspection").build();
    }

    private static InsuranceDto insuranceRequest() {
        return InsuranceDto.builder().vehicleEvent(VehicleEventDto.of(12_000, LocalDate.of(2024, 6, 1)))
            .validFrom(LocalDate.of(2024, 6, 1)).validThru(LocalDate.of(2025, 6, 1))
            .costInCents(10_000).number("FX-INS-2").insurer("Fixture insurer").details("Fixture insurance")
            .insuranceType(InsuranceTypeDto.of(SessionFixtures.DEFAULT_INSURANCE_TYPE, "Fixture insurance")).build();
    }
}
