package com.kasztelanic.carcare.fixtures;

import com.kasztelanic.carcare.domain.Vehicle;
import com.kasztelanic.carcare.repository.FuelTypeRepository;
import com.kasztelanic.carcare.repository.InspectionRepository;
import com.kasztelanic.carcare.repository.InsuranceRepository;
import com.kasztelanic.carcare.repository.InsuranceTypeRepository;
import com.kasztelanic.carcare.repository.RefuelRepository;
import com.kasztelanic.carcare.repository.RepairRepository;
import com.kasztelanic.carcare.repository.RoutineServiceRepository;
import com.kasztelanic.carcare.repository.VehicleRepository;
import com.kasztelanic.carcare.web.rest.AbstractSessionIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SessionFixturesIT extends AbstractSessionIT {

    @Autowired
    private FuelTypeRepository fuelTypeRepository;

    @Autowired
    private InsuranceTypeRepository insuranceTypeRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private RefuelRepository refuelRepository;

    @Autowired
    private RepairRepository repairRepository;

    @Autowired
    private RoutineServiceRepository routineServiceRepository;

    @Autowired
    private InspectionRepository inspectionRepository;

    @Autowired
    private InsuranceRepository insuranceRepository;

    @Test
    @WithMockUser(username = "user")
    void runnerSeedsLookupsAndBuildersPersistEventsForBothOwners() {
        long fuelTypeCount = fuelTypeRepository.count();
        long insuranceTypeCount = insuranceTypeRepository.count();
        long vehicleCount = vehicleRepository.count();
        long refuelCount = refuelRepository.count();
        long repairCount = repairRepository.count();
        long routineServiceCount = routineServiceRepository.count();
        long inspectionCount = inspectionRepository.count();
        long insuranceCount = insuranceRepository.count();

        assertThat(fuelTypeCount).isPositive();
        assertThat(insuranceTypeCount).isPositive();

        assertThatCode(sessionFixtures::seedLookupTypes).doesNotThrowAnyException();
        assertThat(fuelTypeRepository.count()).isEqualTo(fuelTypeCount);
        assertThat(insuranceTypeRepository.count()).isEqualTo(insuranceTypeCount);

        Vehicle userVehicle = sessionFixtures.vehicleWithEventsFor("user");
        Vehicle adminVehicle = sessionFixtures.vehicleWithEventsFor("admin");

        assertThat(userVehicle.getId()).isNotNull();
        assertThat(adminVehicle.getId()).isNotNull();
        assertThat(vehicleRepository.count()).isEqualTo(vehicleCount + 2);
        assertThat(refuelRepository.count()).isEqualTo(refuelCount + 2);
        assertThat(repairRepository.count()).isEqualTo(repairCount + 2);
        assertThat(routineServiceRepository.count()).isEqualTo(routineServiceCount + 2);
        assertThat(inspectionRepository.count()).isEqualTo(inspectionCount + 2);
        assertThat(insuranceRepository.count()).isEqualTo(insuranceCount + 2);
        assertThat(vehicleRepository.findByOwnerIsCurrentUser()).extracting(Vehicle::getId).contains(userVehicle.getId());

        assertThat(userVehicle.getMake()).startsWith("Fixture make ");
        assertThat(userVehicle.getModel()).startsWith("Fixture model ");
        assertThat(userVehicle.getLicensePlate()).matches("FX\\d+");
        assertThat(refuelRepository.findByVehicleId(userVehicle.getId())).singleElement().satisfies(refuel -> {
            assertThat(refuel.getVehicleEvent().getMileage()).isEqualTo(10_000);
            assertThat(refuel.getVehicleEvent().getDate()).isEqualTo(LocalDate.of(2024, 1, 10));
            assertThat(refuel.getCostInCents()).isEqualTo(15_000);
            assertThat(refuel.getVolume()).isEqualTo(45_000);
        });
        assertThat(repairRepository.findByVehicleId(userVehicle.getId())).singleElement().satisfies(repair -> {
            assertThat(repair.getVehicleEvent().getMileage()).isEqualTo(10_100);
            assertThat(repair.getVehicleEvent().getDate()).isEqualTo(LocalDate.of(2024, 2, 10));
            assertThat(repair.getCostInCents()).isEqualTo(25_000);
        });
        assertThat(routineServiceRepository.findByVehicleId(userVehicle.getId())).singleElement().satisfies(service -> {
            assertThat(service.getVehicleEvent().getMileage()).isEqualTo(10_200);
            assertThat(service.getVehicleEvent().getDate()).isEqualTo(LocalDate.of(2024, 3, 10));
            assertThat(service.getCostInCents()).isEqualTo(20_000);
            assertThat(service.getNextByMileage()).isEqualTo(20_000);
            assertThat(service.getNextByDate()).isEqualTo(LocalDate.of(2025, 3, 10));
        });
        assertThat(inspectionRepository.findByVehicleId(userVehicle.getId())).singleElement().satisfies(inspection -> {
            assertThat(inspection.getVehicleEvent().getMileage()).isEqualTo(10_300);
            assertThat(inspection.getVehicleEvent().getDate()).isEqualTo(LocalDate.of(2024, 4, 10));
            assertThat(inspection.getCostInCents()).isEqualTo(15_000);
            assertThat(inspection.getValidThru()).isEqualTo(LocalDate.of(2025, 4, 10));
        });
        assertThat(insuranceRepository.findByVehicleId(userVehicle.getId())).singleElement().satisfies(insurance -> {
            assertThat(insurance.getVehicleEvent().getMileage()).isEqualTo(10_400);
            assertThat(insurance.getVehicleEvent().getDate()).isEqualTo(LocalDate.of(2024, 5, 10));
            assertThat(insurance.getValidFrom()).isEqualTo(LocalDate.of(2024, 5, 10));
            assertThat(insurance.getValidThru()).isEqualTo(LocalDate.of(2025, 5, 10));
            assertThat(insurance.getCostInCents()).isEqualTo(50_000);
        });
    }
}
