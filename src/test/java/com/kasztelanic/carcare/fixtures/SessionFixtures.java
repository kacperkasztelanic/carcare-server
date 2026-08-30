package com.kasztelanic.carcare.fixtures;

import com.kasztelanic.carcare.domain.FuelType;
import com.kasztelanic.carcare.domain.Inspection;
import com.kasztelanic.carcare.domain.Insurance;
import com.kasztelanic.carcare.domain.InsuranceType;
import com.kasztelanic.carcare.domain.Refuel;
import com.kasztelanic.carcare.domain.ReminderAdvance;
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
import com.kasztelanic.carcare.repository.ReminderAdvanceRepository;
import com.kasztelanic.carcare.repository.RepairRepository;
import com.kasztelanic.carcare.repository.RoutineServiceRepository;
import com.kasztelanic.carcare.repository.UserRepository;
import com.kasztelanic.carcare.repository.VehicleRepository;
import com.kasztelanic.carcare.service.ImageStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    public static final LocalDate GOLDEN_REFERENCE_DATE = LocalDate.of(2026, 4, 15);
    public static final Set<String> GOLDEN_HANDLES = Set.of(
        "fuel-type:diesel", "insurance-type:oc", "reminder-advance:three-days", "reminder-advance:seven-days",
        "owner:admin-en", "owner:user-pl", "vehicle:en-primary", "vehicle:pl-primary", "vehicle:zero-consumption",
        "refuel:en-first", "refuel:en-second", "refuel:en-boundary", "refuel:zero-volume", "refuel:pl-only",
        "refuel:zero-consumption", "repair:same-date-low-mileage", "repair:range-before",
        "inspection:same-date-high-mileage", "inspection:en-reminder-plus-three", "inspection:pl-reminder-plus-seven",
        "inspection:reminder-minus-one", "insurance:en-reminder-plus-three", "insurance:pl-reminder-plus-seven",
        "insurance:reminder-plus-one", "routine-service:null-next-date", "routine-service:null-next-mileage",
        "routine-service:en-reminder-plus-three", "routine-service:pl-reminder-plus-seven"
    );
    private static final Set<String> GOLDEN_OWNER_LOGINS = Set.of("admin", "user");

    private final FuelTypeRepository fuelTypeRepository;
    private final InsuranceTypeRepository insuranceTypeRepository;
    private final ReminderAdvanceRepository reminderAdvanceRepository;
    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final RefuelRepository refuelRepository;
    private final RepairRepository repairRepository;
    private final RoutineServiceRepository routineServiceRepository;
    private final InspectionRepository inspectionRepository;
    private final InsuranceRepository insuranceRepository;
    private final CacheManager cacheManager;
    private final ImageStorageService imageStorageService;
    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;
    private final PasswordEncoder passwordEncoder;

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
        return vehicleFor(ownerLogin, defaultFuelType());
    }

    /** Same as {@link #vehicleFor(String)} but on an explicit fuel type — used by the in-use
     * lookup-deletion (409) coverage, which needs a dedicated, disposable {@link FuelType}. */
    public Vehicle vehicleFor(String ownerLogin, FuelType fuelType) {
        long sequence = fixtureSequence.incrementAndGet();
        Vehicle vehicle = Vehicle.builder()
            .make("Fixture make " + sequence)
            .model("Fixture model " + sequence)
            .licensePlate("FX" + sequence)
            .fuelType(fuelType)
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

    /**
     * Archive-lifecycle helpers. Deliberately not called from {@link #seedGoldenDataset()} and not
     * part of {@link #GOLDEN_HANDLES} — the golden reference data contains no archived vehicles and
     * its cardinality and indexes must not move.
     */
    public Vehicle archive(Vehicle vehicle, Instant archivedAt) {
        vehicle.setArchivedAt(archivedAt);
        return vehicleRepository.save(vehicle);
    }

    public Vehicle archivedVehicleFor(String ownerLogin, Instant archivedAt) {
        return archive(vehicleFor(ownerLogin), archivedAt);
    }

    /**
     * Saves {@code bytes} as a real PNG on disk via {@link ImageStorageService}, records the
     * returned filename on the vehicle's details, and persists the vehicle. Used by the purge IT,
     * which must prove the file is deleted after commit.
     */
    public Vehicle imageFor(Vehicle vehicle, byte[] bytes) {
        String fileName = imageStorageService.save(bytes, "image/png");
        vehicle.getVehicleDetails().setImage(fileName);
        return vehicleRepository.save(vehicle);
    }

    /**
     * try/finally cleanup for non-transactional ({@code NOT_SUPPORTED}) ITs: deletes the five event
     * tables' rows then the vehicle rows, FK-safe, by id. Wrapped in a {@link TransactionTemplate}
     * because the test datasource runs with hikari {@code auto-commit=false}, so raw JDBC statements
     * outside a Spring transaction are never committed. Safe in tests — L2 is off in this profile.
     */
    public void purgeRowsFor(Collection<Long> vehicleIds) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (Long id : vehicleIds) {
                for (String table : new String[] {"refuels", "repairs", "routine_services", "inspections",
                    "insurances"}) {
                    jdbcTemplate.update("delete from " + table + " where vehicle_id = ?", id);
                }
                jdbcTemplate.update("delete from vehicles where id = ?", id);
            }
        });
    }

    /**
     * Creates a committed, activated user with a unique login, for commit-path ITs that run
     * under {@code NOT_SUPPORTED} and need the row visible outside their (non-)transaction.
     */
    public String committedUser() {
        long sequence = fixtureSequence.incrementAndGet();
        String login = "committed-" + sequence;
        User user = new User();
        user.setLogin(login);
        user.setPassword(passwordEncoder.encode("disposition-secret"));
        user.setEmail(login + "@example.com");
        user.setFirstName("Disposition");
        user.setLastName("Test");
        user.setActivated(true);
        user.setLangKey("en");
        user.setCreatedBy("system");
        return userRepository.saveAndFlush(user).getLogin();
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
        return refuelFor(vehicle, 10_000, LocalDate.of(2024, 1, 10), 45_000, 15_000, "Fixture station");
    }

    public Refuel refuelFor(Vehicle vehicle, int mileage, LocalDate date) {
        return refuelFor(vehicle, mileage, date, 45_000, 15_000, "Fixture station");
    }

    /** Creates a refuel with explicit volume and cost, retaining the fixture station. */
    public Refuel refuelFor(Vehicle vehicle, int mileage, LocalDate date, int volume, int costInCents) {
        return refuelFor(vehicle, mileage, date, volume, costInCents, "Fixture station");
    }

    /** Creates a refuel with every value that affects reports and statistics. */
    public Refuel refuelFor(Vehicle vehicle, int mileage, LocalDate date, int volume, int costInCents,
                            String station) {
        return refuelRepository.save(Refuel.builder()
            .vehicleEvent(VehicleEvent.of(mileage, date))
            .vehicle(vehicle)
            .costInCents(costInCents)
            .volume(volume)
            .station(station)
            .build());
    }

    public Repair repairFor(Vehicle vehicle) {
        return repairFor(vehicle, 10_100, LocalDate.of(2024, 2, 10), 25_000,
            "Fixture station", "Fixture repair");
    }

    /** Creates a repair with explicit mileage, date, and cost. */
    public Repair repairFor(Vehicle vehicle, int mileage, LocalDate date, int costInCents) {
        return repairFor(vehicle, mileage, date, costInCents, "Fixture station", "Fixture repair");
    }

    /** Creates a repair with every persisted value. */
    public Repair repairFor(Vehicle vehicle, int mileage, LocalDate date, int costInCents,
                            String station, String details) {
        return repairRepository.save(Repair.builder()
            .vehicleEvent(VehicleEvent.of(mileage, date))
            .costInCents(costInCents)
            .station(station)
            .details(details)
            .vehicle(vehicle)
            .build());
    }

    public RoutineService routineServiceFor(Vehicle vehicle) {
        return routineServiceFor(vehicle, 10_200, LocalDate.of(2024, 3, 10), 20_000,
            20_000, LocalDate.of(2025, 3, 10), "Fixture station", "Fixture routine service");
    }

    /** Creates a routine service with explicit nullable next-by values. */
    public RoutineService routineServiceFor(Vehicle vehicle, int mileage, LocalDate date, int costInCents,
                                             Integer nextByMileage, LocalDate nextByDate) {
        return routineServiceFor(vehicle, mileage, date, costInCents, nextByMileage, nextByDate,
            "Fixture station", "Fixture routine service");
    }

    /** Creates a routine service with every persisted value. */
    public RoutineService routineServiceFor(Vehicle vehicle, int mileage, LocalDate date, int costInCents,
                                             Integer nextByMileage, LocalDate nextByDate, String station,
                                             String details) {
        return routineServiceRepository.save(RoutineService.builder()
            .vehicleEvent(VehicleEvent.of(mileage, date))
            .vehicle(vehicle)
            .costInCents(costInCents)
            .nextByMileage(nextByMileage)
            .nextByDate(nextByDate)
            .station(station)
            .details(details)
            .build());
    }

    public Inspection inspectionFor(Vehicle vehicle) {
        return inspectionFor(vehicle, 10_300, LocalDate.of(2024, 4, 10), 15_000,
            LocalDate.of(2025, 4, 10), "Fixture station", "Fixture inspection");
    }

    /** Creates an inspection with explicit mileage, date, cost, and validity. */
    public Inspection inspectionFor(Vehicle vehicle, int mileage, LocalDate date, int costInCents,
                                    LocalDate validThru) {
        return inspectionFor(vehicle, mileage, date, costInCents, validThru,
            "Fixture station", "Fixture inspection");
    }

    /** Creates an inspection with every persisted value. */
    public Inspection inspectionFor(Vehicle vehicle, int mileage, LocalDate date, int costInCents,
                                    LocalDate validThru, String station, String details) {
        return inspectionRepository.save(Inspection.builder()
            .vehicleEvent(VehicleEvent.of(mileage, date))
            .costInCents(costInCents)
            .station(station)
            .validThru(validThru)
            .details(details)
            .vehicle(vehicle)
            .build());
    }

    public Insurance insuranceFor(Vehicle vehicle) {
        return insuranceFor(vehicle, 10_400, LocalDate.of(2024, 5, 10),
            LocalDate.of(2024, 5, 10), LocalDate.of(2025, 5, 10), 50_000,
            "FX-INS-1", "Fixture insurer", "Fixture insurance");
    }

    /** Creates insurance with explicit dates and cost, retaining fixture text values. */
    public Insurance insuranceFor(Vehicle vehicle, int mileage, LocalDate date, LocalDate validFrom,
                                  LocalDate validThru, int costInCents) {
        return insuranceFor(vehicle, mileage, date, validFrom, validThru, costInCents,
            "FX-INS-1", "Fixture insurer", "Fixture insurance");
    }

    /** Creates insurance with every persisted value. */
    public Insurance insuranceFor(Vehicle vehicle, int mileage, LocalDate date, LocalDate validFrom,
                                  LocalDate validThru, int costInCents, String number, String insurer,
                                  String details) {
        return insuranceFor(vehicle, mileage, date, validFrom, validThru, costInCents, number, insurer, details,
            defaultInsuranceType());
    }

    /** Creates insurance with an explicit lookup type as well as every persisted value. */
    public Insurance insuranceFor(Vehicle vehicle, int mileage, LocalDate date, LocalDate validFrom,
                                  LocalDate validThru, int costInCents, String number, String insurer,
                                  String details, InsuranceType insuranceType) {
        return insuranceRepository.save(Insurance.builder()
            .vehicleEvent(VehicleEvent.of(mileage, date))
            .validFrom(validFrom)
            .validThru(validThru)
            .costInCents(costInCents)
            .number(number)
            .insurer(insurer)
            .details(details)
            .insuranceType(insuranceType)
            .vehicle(vehicle)
            .build());
    }

    /**
     * Seeds the SQL fixture's logical rows inside the caller's transaction.  It intentionally has
     * no idempotence flag: {@code AbstractSessionIT} rolls this transaction back, so each test must
     * create a fresh map for the rows that actually exist in its transaction.
     *
     * @return symbolic fixture handle to the generated database id
     */
    public Map<String, Long> seedGoldenDataset() {
        FuelType diesel = fuelTypeRepository.findByType("DIESEL")
            .orElseGet(() -> fuelTypeRepository.save(FuelType.of("DIESEL", "Diesel", "Diesel")));
        InsuranceType oc = insuranceTypeRepository.findByType("OC")
            .orElseGet(() -> insuranceTypeRepository.save(InsuranceType.of("OC", "Liability", "OC")));
        ReminderAdvance threeDays = reminderAdvanceRepository.findByDays(3)
            .orElseGet(() -> reminderAdvanceRepository.save(ReminderAdvance.of(3)));
        ReminderAdvance sevenDays = reminderAdvanceRepository.findByDays(7)
            .orElseGet(() -> reminderAdvanceRepository.save(ReminderAdvance.of(7)));

        User admin = ownerFor("admin");
        admin.setLangKey("en");
        userRepository.save(admin);
        User user = ownerFor("user");
        user.setLangKey("pl");
        userRepository.save(user);
        evictGoldenOwnerCaches();

        Vehicle en = goldenVehicle("Ford", "Focus", "EN 1001", "Titanium", "Golden EN vehicle",
            "REG-EN-001", "CARD-EN", "ENPRIMARY00000001", 110, 1997, 1420, admin, diesel);
        Vehicle pl = goldenVehicle("Toyota", "Corolla", "PL 1002", "Hybrid", "Golden PL vehicle",
            "REG-PL-002", "CARD-PL", "PLPRIMARY00000002", 90, 1798, 1380, user, diesel);
        Vehicle zero = goldenVehicle("Mazda", "Three", "EN 1003", null, "Single refuel vehicle",
            "REG-EN-003", null, "ZEROCONSUMPTION01", 88, 1598, 1300, admin, diesel);

        Map<String, Long> handles = new LinkedHashMap<>();
        handles.put("fuel-type:diesel", diesel.getId());
        handles.put("insurance-type:oc", oc.getId());
        handles.put("reminder-advance:three-days", threeDays.getId());
        handles.put("reminder-advance:seven-days", sevenDays.getId());
        handles.put("owner:admin-en", admin.getId());
        handles.put("owner:user-pl", user.getId());
        handles.put("vehicle:en-primary", en.getId());
        handles.put("vehicle:pl-primary", pl.getId());
        handles.put("vehicle:zero-consumption", zero.getId());

        handles.put("refuel:en-first", refuelFor(en, 10_000, LocalDate.of(2026, 3, 1), 45_000, 27_000,
            "North Fuel").getId());
        handles.put("refuel:en-second", refuelFor(en, 10_500, LocalDate.of(2026, 3, 15), 42_000, 25_200,
            "North Fuel").getId());
        handles.put("refuel:en-boundary", refuelFor(en, 11_000, LocalDate.of(2026, 3, 31), 40_000, 24_800,
            "North Fuel").getId());
        handles.put("refuel:zero-volume", refuelFor(en, 11_500, LocalDate.of(2026, 4, 1), 0, 100,
            "Zero Volume").getId());
        handles.put("refuel:pl-only", refuelFor(pl, 5_000, LocalDate.of(2026, 3, 20), 50_000, 30_000,
            "Polska Fuel").getId());
        handles.put("refuel:zero-consumption", refuelFor(zero, 20_000, LocalDate.of(2026, 3, 15), 30_000, 18_000,
            "One Fill").getId());

        handles.put("repair:same-date-low-mileage", repairFor(en, 10_800, LocalDate.of(2026, 3, 25), 12_500,
            "EN Garage", "Brake pads").getId());
        handles.put("repair:range-before", repairFor(en, 9_800, LocalDate.of(2026, 2, 28), 9_900,
            "EN Garage", "Old repair").getId());

        handles.put("inspection:same-date-high-mileage", inspectionFor(en, 10_900, LocalDate.of(2026, 3, 25),
            15_000, LocalDate.of(2027, 3, 25), "EN Station", "Annual inspection").getId());
        handles.put("inspection:en-reminder-plus-three", inspectionFor(en, 10_300, LocalDate.of(2026, 3, 10),
            16_000, LocalDate.of(2026, 4, 18), "EN Station", "Due in three").getId());
        handles.put("inspection:pl-reminder-plus-seven", inspectionFor(pl, 4_800, LocalDate.of(2026, 3, 12),
            17_000, LocalDate.of(2026, 4, 22), "PL Station", "Due in seven").getId());
        handles.put("inspection:reminder-minus-one", inspectionFor(pl, 4_900, LocalDate.of(2026, 3, 13),
            18_000, LocalDate.of(2026, 4, 21), "PL Station", "Too early").getId());

        handles.put("insurance:en-reminder-plus-three", insuranceFor(en, 10_100, LocalDate.of(2026, 3, 5),
            LocalDate.of(2025, 4, 18), LocalDate.of(2026, 4, 18), 42_000, "EN-OC-1", "Insure EN",
            "EN policy", oc).getId());
        handles.put("insurance:pl-reminder-plus-seven", insuranceFor(pl, 4_700, LocalDate.of(2026, 3, 6),
            LocalDate.of(2025, 4, 22), LocalDate.of(2026, 4, 22), 43_000, "PL-OC-1", "Insure PL",
            "PL policy", oc).getId());
        handles.put("insurance:reminder-plus-one", insuranceFor(en, 10_200, LocalDate.of(2026, 3, 7),
            LocalDate.of(2025, 4, 19), LocalDate.of(2026, 4, 19), 44_000, "EN-OC-2", "Insure EN",
            "Too late", oc).getId());

        handles.put("routine-service:null-next-date", routineServiceFor(en, 10_300, LocalDate.of(2026, 3, 10),
            15_000, null, null, "EN Service", "No next date").getId());
        handles.put("routine-service:null-next-mileage", routineServiceFor(en, 10_700, LocalDate.of(2026, 3, 20),
            20_000, null, LocalDate.of(2026, 5, 1), "EN Service", "No next mileage").getId());
        handles.put("routine-service:en-reminder-plus-three", routineServiceFor(en, 10_400, LocalDate.of(2026, 3, 11),
            22_000, 12_000, LocalDate.of(2026, 4, 18), "EN Service", "Due in three").getId());
        handles.put("routine-service:pl-reminder-plus-seven", routineServiceFor(pl, 4_950, LocalDate.of(2026, 3, 14),
            23_000, 7_000, LocalDate.of(2026, 4, 22), "PL Service", "Due in seven").getId());

        return Collections.unmodifiableMap(handles);
    }

    /**
     * Evicts direct fixture writes from the login cache so later readers observe their persisted
     * language keys, including readers in a different transaction or test class.
     */
    public void evictGoldenOwnerCaches() {
        var usersByLogin = Objects.requireNonNull(
            cacheManager.getCache(UserRepository.USERS_BY_LOGIN_CACHE),
            "usersByLogin cache is not configured");
        GOLDEN_OWNER_LOGINS.forEach(usersByLogin::evict);
    }

    private Vehicle goldenVehicle(String make, String model, String licensePlate, String modelSuffix, String notes,
                                  String registrationCertificate, String vehicleCard, String vinNumber,
                                  int enginePower, int engineVolume, int weight, User owner, FuelType fuelType) {
        return vehicleRepository.save(Vehicle.builder()
            .make(make)
            .model(model)
            .licensePlate(licensePlate)
            .fuelType(fuelType)
            .vehicleDetails(VehicleDetails.builder()
                .modelSuffix(modelSuffix)
                .vinNumber(vinNumber)
                .vehicleCard(vehicleCard)
                .registrationCertificate(registrationCertificate)
                .yearOfManufacture(model.equals("Focus") ? 2019 : model.equals("Corolla") ? 2020 : 2018)
                .engineVolume(engineVolume)
                .enginePower(enginePower)
                .weight(weight)
                .notes(notes)
                .image(null)
                .build())
            .owner(owner)
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
