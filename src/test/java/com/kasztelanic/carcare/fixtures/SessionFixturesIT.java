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
    }
}
