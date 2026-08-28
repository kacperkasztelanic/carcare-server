package com.kasztelanic.carcare.golden;

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
import com.kasztelanic.carcare.fixtures.SessionFixtures;
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
import com.kasztelanic.carcare.web.rest.AbstractSessionIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Proves that the H2-side builders mirror the rows captured from the baseline SQL fixture. */
class GoldenDatasetMirrorIT extends AbstractSessionIT {

    @Autowired
    private FuelTypeRepository fuelTypeRepository;

    @Autowired
    private InsuranceTypeRepository insuranceTypeRepository;

    @Autowired
    private ReminderAdvanceRepository reminderAdvanceRepository;

    @Autowired
    private UserRepository userRepository;

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
    void goldenDatasetMirrorsTheSqlRowsAndReturnsEveryHandle() {
        long fuelTypeCount = fuelTypeRepository.count();
        long insuranceTypeCount = insuranceTypeRepository.count();
        long reminderAdvanceCount = reminderAdvanceRepository.count();
        long vehicleCount = vehicleRepository.count();
        long refuelCount = refuelRepository.count();
        long repairCount = repairRepository.count();
        long routineServiceCount = routineServiceRepository.count();
        long inspectionCount = inspectionRepository.count();
        long insuranceCount = insuranceRepository.count();

        Map<String, Long> ids = sessionFixtures.seedGoldenDataset();

        assertThat(ids.keySet()).containsExactlyInAnyOrderElementsOf(SessionFixtures.GOLDEN_HANDLES);
        assertThat(ids).hasSize(SessionFixtures.GOLDEN_HANDLES.size());
        assertThat(fuelTypeRepository.count()).isEqualTo(fuelTypeCount + 1);
        assertThat(insuranceTypeRepository.count()).isEqualTo(insuranceTypeCount + 1);
        assertThat(reminderAdvanceRepository.count()).isEqualTo(reminderAdvanceCount + 2);
        assertThat(vehicleRepository.count()).isEqualTo(vehicleCount + 3);
        assertThat(refuelRepository.count()).isEqualTo(refuelCount + 6);
        assertThat(repairRepository.count()).isEqualTo(repairCount + 2);
        assertThat(routineServiceRepository.count()).isEqualTo(routineServiceCount + 4);
        assertThat(inspectionRepository.count()).isEqualTo(inspectionCount + 4);
        assertThat(insuranceRepository.count()).isEqualTo(insuranceCount + 3);

        FuelType diesel = fuelTypeRepository.findById(ids.get("fuel-type:diesel")).orElseThrow();
        assertThat(diesel.getType()).isEqualTo("DIESEL");
        assertThat(diesel.getEnglishTranslation()).isEqualTo("Diesel");
        assertThat(diesel.getPolishTranslation()).isEqualTo("Diesel");

        InsuranceType oc = insuranceTypeRepository.findById(ids.get("insurance-type:oc")).orElseThrow();
        assertThat(oc.getType()).isEqualTo("OC");
        assertThat(oc.getEnglishTranslation()).isEqualTo("Liability");
        assertThat(oc.getPolishTranslation()).isEqualTo("OC");

        ReminderAdvance threeDays = reminderAdvanceRepository.findById(ids.get("reminder-advance:three-days"))
            .orElseThrow();
        ReminderAdvance sevenDays = reminderAdvanceRepository.findById(ids.get("reminder-advance:seven-days"))
            .orElseThrow();
        assertThat(threeDays.getDays()).isEqualTo(3);
        assertThat(sevenDays.getDays()).isEqualTo(7);

        User admin = userRepository.findById(ids.get("owner:admin-en")).orElseThrow();
        User user = userRepository.findById(ids.get("owner:user-pl")).orElseThrow();
        assertThat(admin.getLogin()).isEqualTo("admin");
        assertThat(admin.getLangKey()).isEqualTo("en");
        assertThat(user.getLogin()).isEqualTo("user");
        assertThat(user.getLangKey()).isEqualTo("pl");

        Vehicle en = vehicle(ids, "vehicle:en-primary");
        Vehicle pl = vehicle(ids, "vehicle:pl-primary");
        Vehicle zero = vehicle(ids, "vehicle:zero-consumption");
        assertVehicle(en, admin, diesel, "Ford", "Focus", "EN 1001", "Titanium", "Golden EN vehicle",
            "REG-EN-001", "CARD-EN", "ENPRIMARY00000001", 2019, 1997, 110, 1420);
        assertVehicle(pl, user, diesel, "Toyota", "Corolla", "PL 1002", "Hybrid", "Golden PL vehicle",
            "REG-PL-002", "CARD-PL", "PLPRIMARY00000002", 2020, 1798, 90, 1380);
        assertVehicle(zero, admin, diesel, "Mazda", "Three", "EN 1003", null, "Single refuel vehicle",
            "REG-EN-003", null, "ZEROCONSUMPTION01", 2018, 1598, 88, 1300);

        assertRefuel(ids, "refuel:en-first", en, 10_000, LocalDate.of(2026, 3, 1), 27_000, 45_000, "North Fuel");
        assertRefuel(ids, "refuel:en-second", en, 10_500, LocalDate.of(2026, 3, 15), 25_200, 42_000, "North Fuel");
        assertRefuel(ids, "refuel:en-boundary", en, 11_000, LocalDate.of(2026, 3, 31), 24_800, 40_000, "North Fuel");
        assertRefuel(ids, "refuel:zero-volume", en, 11_500, LocalDate.of(2026, 4, 1), 100, 0, "Zero Volume");
        assertRefuel(ids, "refuel:pl-only", pl, 5_000, LocalDate.of(2026, 3, 20), 30_000, 50_000, "Polska Fuel");
        assertRefuel(ids, "refuel:zero-consumption", zero, 20_000, LocalDate.of(2026, 3, 15), 18_000, 30_000, "One Fill");

        assertRepair(ids, "repair:same-date-low-mileage", en, 10_800, LocalDate.of(2026, 3, 25), 12_500,
            "EN Garage", "Brake pads");
        assertRepair(ids, "repair:range-before", en, 9_800, LocalDate.of(2026, 2, 28), 9_900,
            "EN Garage", "Old repair");

        assertInspection(ids, "inspection:same-date-high-mileage", en, 10_900, LocalDate.of(2026, 3, 25),
            15_000, LocalDate.of(2027, 3, 25), "EN Station", "Annual inspection");
        assertInspection(ids, "inspection:en-reminder-plus-three", en, 10_300, LocalDate.of(2026, 3, 10),
            16_000, LocalDate.of(2026, 4, 18), "EN Station", "Due in three");
        assertInspection(ids, "inspection:pl-reminder-plus-seven", pl, 4_800, LocalDate.of(2026, 3, 12),
            17_000, LocalDate.of(2026, 4, 22), "PL Station", "Due in seven");
        assertInspection(ids, "inspection:reminder-minus-one", pl, 4_900, LocalDate.of(2026, 3, 13),
            18_000, LocalDate.of(2026, 4, 21), "PL Station", "Too early");

        assertInsurance(ids, "insurance:en-reminder-plus-three", en, 10_100, LocalDate.of(2026, 3, 5),
            LocalDate.of(2025, 4, 18), LocalDate.of(2026, 4, 18), 42_000, "EN-OC-1", "Insure EN", "EN policy", oc);
        assertInsurance(ids, "insurance:pl-reminder-plus-seven", pl, 4_700, LocalDate.of(2026, 3, 6),
            LocalDate.of(2025, 4, 22), LocalDate.of(2026, 4, 22), 43_000, "PL-OC-1", "Insure PL", "PL policy", oc);
        assertInsurance(ids, "insurance:reminder-plus-one", en, 10_200, LocalDate.of(2026, 3, 7),
            LocalDate.of(2025, 4, 19), LocalDate.of(2026, 4, 19), 44_000, "EN-OC-2", "Insure EN", "Too late", oc);

        assertRoutineService(ids, "routine-service:null-next-date", en, 10_300, LocalDate.of(2026, 3, 10),
            15_000, null, null, "EN Service", "No next date");
        assertRoutineService(ids, "routine-service:null-next-mileage", en, 10_700, LocalDate.of(2026, 3, 20),
            20_000, null, LocalDate.of(2026, 5, 1), "EN Service", "No next mileage");
        assertRoutineService(ids, "routine-service:en-reminder-plus-three", en, 10_400, LocalDate.of(2026, 3, 11),
            22_000, 12_000, LocalDate.of(2026, 4, 18), "EN Service", "Due in three");
        assertRoutineService(ids, "routine-service:pl-reminder-plus-seven", pl, 4_950, LocalDate.of(2026, 3, 14),
            23_000, 7_000, LocalDate.of(2026, 4, 22), "PL Service", "Due in seven");
    }

    private Vehicle vehicle(Map<String, Long> ids, String handle) {
        return vehicleRepository.findById(ids.get(handle)).orElseThrow();
    }

    private static void assertVehicle(Vehicle vehicle, User owner, FuelType fuelType, String make, String model,
                                      String licensePlate, String modelSuffix, String notes,
                                      String registrationCertificate, String vehicleCard, String vinNumber,
                                      int year, int engineVolume, int enginePower, int weight) {
        assertThat(vehicle.getOwner()).isEqualTo(owner);
        assertThat(vehicle.getFuelType()).isEqualTo(fuelType);
        assertThat(vehicle.getMake()).isEqualTo(make);
        assertThat(vehicle.getModel()).isEqualTo(model);
        assertThat(vehicle.getLicensePlate()).isEqualTo(licensePlate);
        VehicleDetails details = vehicle.getVehicleDetails();
        assertThat(details.getModelSuffix()).isEqualTo(modelSuffix);
        assertThat(details.getNotes()).isEqualTo(notes);
        assertThat(details.getRegistrationCertificate()).isEqualTo(registrationCertificate);
        assertThat(details.getVehicleCard()).isEqualTo(vehicleCard);
        assertThat(details.getVinNumber()).isEqualTo(vinNumber);
        assertThat(details.getYearOfManufacture()).isEqualTo(year);
        assertThat(details.getEngineVolume()).isEqualTo(engineVolume);
        assertThat(details.getEnginePower()).isEqualTo(enginePower);
        assertThat(details.getWeight()).isEqualTo(weight);
        assertThat(details.getImage()).isNull();
    }

    private void assertRefuel(Map<String, Long> ids, String handle, Vehicle vehicle, int mileage, LocalDate date,
                              int costInCents, int volume, String station) {
        Refuel refuel = refuelRepository.findById(ids.get(handle)).orElseThrow();
        assertThat(refuel.getVehicle()).isEqualTo(vehicle);
        assertThat(refuel.getVehicleEvent().getMileage()).isEqualTo(mileage);
        assertThat(refuel.getVehicleEvent().getDate()).isEqualTo(date);
        assertThat(refuel.getCostInCents()).isEqualTo(costInCents);
        assertThat(refuel.getVolume()).isEqualTo(volume);
        assertThat(refuel.getStation()).isEqualTo(station);
    }

    private void assertRepair(Map<String, Long> ids, String handle, Vehicle vehicle, int mileage, LocalDate date,
                              int costInCents, String station, String details) {
        Repair repair = repairRepository.findById(ids.get(handle)).orElseThrow();
        assertThat(repair.getVehicle()).isEqualTo(vehicle);
        assertThat(repair.getVehicleEvent().getMileage()).isEqualTo(mileage);
        assertThat(repair.getVehicleEvent().getDate()).isEqualTo(date);
        assertThat(repair.getCostInCents()).isEqualTo(costInCents);
        assertThat(repair.getStation()).isEqualTo(station);
        assertThat(repair.getDetails()).isEqualTo(details);
    }

    private void assertRoutineService(Map<String, Long> ids, String handle, Vehicle vehicle, int mileage,
                                      LocalDate date, int costInCents, Integer nextByMileage, LocalDate nextByDate,
                                      String station, String details) {
        RoutineService routineService = routineServiceRepository.findById(ids.get(handle)).orElseThrow();
        assertThat(routineService.getVehicle()).isEqualTo(vehicle);
        assertThat(routineService.getVehicleEvent().getMileage()).isEqualTo(mileage);
        assertThat(routineService.getVehicleEvent().getDate()).isEqualTo(date);
        assertThat(routineService.getCostInCents()).isEqualTo(costInCents);
        assertThat(routineService.getNextByMileage()).isEqualTo(nextByMileage);
        assertThat(routineService.getNextByDate()).isEqualTo(nextByDate);
        assertThat(routineService.getStation()).isEqualTo(station);
        assertThat(routineService.getDetails()).isEqualTo(details);
    }

    private void assertInspection(Map<String, Long> ids, String handle, Vehicle vehicle, int mileage, LocalDate date,
                                  int costInCents, LocalDate validThru, String station, String details) {
        Inspection inspection = inspectionRepository.findById(ids.get(handle)).orElseThrow();
        assertThat(inspection.getVehicle()).isEqualTo(vehicle);
        assertThat(inspection.getVehicleEvent().getMileage()).isEqualTo(mileage);
        assertThat(inspection.getVehicleEvent().getDate()).isEqualTo(date);
        assertThat(inspection.getCostInCents()).isEqualTo(costInCents);
        assertThat(inspection.getValidThru()).isEqualTo(validThru);
        assertThat(inspection.getStation()).isEqualTo(station);
        assertThat(inspection.getDetails()).isEqualTo(details);
    }

    private void assertInsurance(Map<String, Long> ids, String handle, Vehicle vehicle, int mileage, LocalDate date,
                                 LocalDate validFrom, LocalDate validThru, int costInCents, String number,
                                 String insurer, String details, InsuranceType insuranceType) {
        Insurance insurance = insuranceRepository.findById(ids.get(handle)).orElseThrow();
        assertThat(insurance.getVehicle()).isEqualTo(vehicle);
        assertThat(insurance.getVehicleEvent().getMileage()).isEqualTo(mileage);
        assertThat(insurance.getVehicleEvent().getDate()).isEqualTo(date);
        assertThat(insurance.getValidFrom()).isEqualTo(validFrom);
        assertThat(insurance.getValidThru()).isEqualTo(validThru);
        assertThat(insurance.getCostInCents()).isEqualTo(costInCents);
        assertThat(insurance.getNumber()).isEqualTo(number);
        assertThat(insurance.getInsurer()).isEqualTo(insurer);
        assertThat(insurance.getDetails()).isEqualTo(details);
        assertThat(insurance.getInsuranceType()).isEqualTo(insuranceType);
    }
}
