package com.kasztelanic.carcare.fixtures;

import com.kasztelanic.carcare.domain.FuelType;
import com.kasztelanic.carcare.domain.Inspection;
import com.kasztelanic.carcare.domain.Insurance;
import com.kasztelanic.carcare.domain.InsuranceType;
import com.kasztelanic.carcare.domain.Refuel;
import com.kasztelanic.carcare.domain.Repair;
import com.kasztelanic.carcare.domain.RoutineService;
import com.kasztelanic.carcare.domain.User;
import com.kasztelanic.carcare.domain.Vehicle;
import com.kasztelanic.carcare.domain.VehicleDetails;
import com.kasztelanic.carcare.domain.VehicleEvent;
import com.kasztelanic.carcare.repository.FuelTypeRepository;
import com.kasztelanic.carcare.repository.InspectionRepository;
import com.kasztelanic.carcare.repository.InsuranceRepository;
import com.kasztelanic.carcare.repository.InsuranceTypeRepository;
import com.kasztelanic.carcare.repository.RefuelRepository;
import com.kasztelanic.carcare.repository.RepairRepository;
import com.kasztelanic.carcare.repository.RoutineServiceRepository;
import com.kasztelanic.carcare.repository.UserRepository;
import com.kasztelanic.carcare.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test-only data builders for the S-01 session suite. Lookup rows are seeded when each test
 * context starts so they are visible outside test transactions; per-test vehicles and events are
 * ordinary repository writes and are rolled back by {@code AbstractSessionIT}.
 */
@Component
@Profile("test")
@RequiredArgsConstructor
public class SessionFixtures implements ApplicationRunner {

    public static final String DEFAULT_FUEL_TYPE = "fixture-fuel";
    public static final String DEFAULT_INSURANCE_TYPE = "fixture";

    private final FuelTypeRepository fuelTypeRepository;
    private final InsuranceTypeRepository insuranceTypeRepository;
    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final RefuelRepository refuelRepository;
    private final RepairRepository repairRepository;
    private final RoutineServiceRepository routineServiceRepository;
    private final InspectionRepository inspectionRepository;
    private final InsuranceRepository insuranceRepository;

    private final AtomicLong fixtureSequence = new AtomicLong();

    @Override
    public void run(ApplicationArguments args) {
        seedLookupTypes();
    }

    /**
     * Creates missing lookup rows without replacing existing ones, making this safe when a second
     * Spring context starts against the shared H2 database.
     */
    public void seedLookupTypes() {
        fuelTypeRepository.findByType(DEFAULT_FUEL_TYPE)
            .orElseGet(() -> fuelTypeRepository.save(
                FuelType.of(DEFAULT_FUEL_TYPE, "Fixture fuel", "Paliwo testowe")
            ));
        insuranceTypeRepository.findByType(DEFAULT_INSURANCE_TYPE)
            .orElseGet(() -> insuranceTypeRepository.save(
                InsuranceType.of(DEFAULT_INSURANCE_TYPE, "Fixture insurance", "Ubezpieczenie testowe")
            ));
    }

    public Vehicle vehicleFor(String ownerLogin) {
        long sequence = fixtureSequence.incrementAndGet();
        Vehicle vehicle = Vehicle.builder()
            .make("Fixture make " + sequence)
            .model("Fixture model " + sequence)
            .licensePlate("FX" + sequence)
            .fuelType(defaultFuelType())
            .vehicleDetails(VehicleDetails.builder()
                .modelSuffix("")
                .vinNumber("")
                .vehicleCard("")
                .registrationCertificate("")
                .yearOfManufacture(0)
                .engineVolume(0)
                .enginePower(0)
                .weight(0)
                .notes("")
                .image("")
                .build())
            .owner(ownerFor(ownerLogin))
            .build();
        return vehicleRepository.save(vehicle);
    }

    public Vehicle vehicleWithEventsFor(String ownerLogin) {
        Vehicle vehicle = vehicleFor(ownerLogin);
        refuelFor(vehicle);
        repairFor(vehicle);
        routineServiceFor(vehicle);
        inspectionFor(vehicle);
        insuranceFor(vehicle);
        return vehicle;
    }

    public Refuel refuelFor(Vehicle vehicle) {
        return refuelFor(vehicle, 10_000, LocalDate.of(2024, 1, 10));
    }

    public Refuel refuelFor(Vehicle vehicle, int mileage, LocalDate date) {
        return refuelRepository.save(Refuel.builder()
            .vehicleEvent(VehicleEvent.of(mileage, date))
            .vehicle(vehicle)
            .costInCents(15_000)
            .volume(45_000)
            .station("Fixture station")
            .build());
    }

    public Repair repairFor(Vehicle vehicle) {
        return repairRepository.save(Repair.builder()
            .vehicleEvent(VehicleEvent.of(10_100, LocalDate.of(2024, 2, 10)))
            .costInCents(25_000)
            .station("Fixture station")
            .details("Fixture repair")
            .vehicle(vehicle)
            .build());
    }

    public RoutineService routineServiceFor(Vehicle vehicle) {
        return routineServiceRepository.save(RoutineService.builder()
            .vehicleEvent(VehicleEvent.of(10_200, LocalDate.of(2024, 3, 10)))
            .vehicle(vehicle)
            .costInCents(20_000)
            .nextByMileage(20_000)
            .nextByDate(LocalDate.of(2025, 3, 10))
            .station("Fixture station")
            .details("Fixture routine service")
            .build());
    }

    public Inspection inspectionFor(Vehicle vehicle) {
        return inspectionRepository.save(Inspection.builder()
            .vehicleEvent(VehicleEvent.of(10_300, LocalDate.of(2024, 4, 10)))
            .costInCents(15_000)
            .station("Fixture station")
            .validThru(LocalDate.of(2025, 4, 10))
            .details("Fixture inspection")
            .vehicle(vehicle)
            .build());
    }

    public Insurance insuranceFor(Vehicle vehicle) {
        return insuranceRepository.save(Insurance.builder()
            .vehicleEvent(VehicleEvent.of(10_400, LocalDate.of(2024, 5, 10)))
            .validFrom(LocalDate.of(2024, 5, 10))
            .validThru(LocalDate.of(2025, 5, 10))
            .costInCents(50_000)
            .number("FX-INS-1")
            .insurer("Fixture insurer")
            .details("Fixture insurance")
            .insuranceType(defaultInsuranceType())
            .vehicle(vehicle)
            .build());
    }

    private FuelType defaultFuelType() {
        return fuelTypeRepository.findByType(DEFAULT_FUEL_TYPE)
            .orElseThrow(() -> new IllegalStateException("Session fixture fuel type was not seeded"));
    }

    private InsuranceType defaultInsuranceType() {
        return insuranceTypeRepository.findByType(DEFAULT_INSURANCE_TYPE)
            .orElseThrow(() -> new IllegalStateException("Session fixture insurance type was not seeded"));
    }

    private User ownerFor(String login) {
        return userRepository.findOneByLogin(login)
            .orElseThrow(() -> new IllegalArgumentException("No seeded test owner with login: " + login));
    }
}
