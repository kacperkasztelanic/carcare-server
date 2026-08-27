-- Golden baseline dataset for 6e19b96; reference date: 2026-04-15 UTC.
-- Every inserted row has a unique symbolic handle. IDs 900000+ are reserved for this fixture.

-- fuel-type:diesel
INSERT INTO fuel_types (id, english, polish, type) VALUES (900001, 'Diesel', 'Diesel', 'DIESEL');
-- insurance-type:oc
INSERT INTO insurance_types (id, english, polish, type) VALUES (900011, 'Liability', 'OC', 'OC');
-- reminder-advance:three-days; reminder-advance:seven-days
INSERT INTO reminder_advances (id, type) VALUES (900021, 3), (900022, 7);

-- owner:admin-en; owner:user-pl (Liquibase creates these users with ids 3 and 4.)
UPDATE jhi_user SET lang_key = 'en' WHERE id = 3;
UPDATE jhi_user SET lang_key = 'pl' WHERE id = 4;

-- vehicle:en-primary; vehicle:pl-primary; vehicle:zero-consumption
INSERT INTO vehicles (id, license_plate, make, model, engine_power_in_kw, engine_volume_in_cm3,
                      image, model_suffix, notes, registration_certificate, vehicle_card,
                      vin_number, weight_in_kg, year_of_manufacture, fuel_type_id, owner_id)
VALUES
  (900101, 'EN 1001', 'Ford', 'Focus', 110, 1997, NULL, 'Titanium', 'Golden EN vehicle',
   'REG-EN-001', 'CARD-EN', 'ENPRIMARY00000001', 1420, 2019, 900001, 3),
  (900102, 'PL 1002', 'Toyota', 'Corolla', 90, 1798, NULL, 'Hybrid', 'Golden PL vehicle',
   'REG-PL-002', 'CARD-PL', 'PLPRIMARY00000002', 1380, 2020, 900001, 4),
  (900103, 'EN 1003', 'Mazda', 'Three', 88, 1598, NULL, NULL, 'Single refuel vehicle',
   'REG-EN-003', NULL, 'ZEROCONSUMPTION01', 1300, 2018, 900001, 3);

-- refuel:en-first; refuel:en-second; refuel:en-boundary; refuel:zero-volume
-- refuel:pl-only; refuel:zero-consumption
INSERT INTO refuels (id, cost_in_cents, station, date, mileage, volume_in_cm3, vehicle_id) VALUES
  (900401, 27000, 'North Fuel', '2026-03-01', 10000, 45000, 900101),
  (900402, 25200, 'North Fuel', '2026-03-15', 10500, 42000, 900101),
  (900403, 24800, 'North Fuel', '2026-03-31', 11000, 40000, 900101),
  (900404, 100, 'Zero Volume', '2026-04-01', 11500, 0, 900101),
  (900405, 30000, 'Polska Fuel', '2026-03-20', 5000, 50000, 900102),
  (900406, 18000, 'One Fill', '2026-03-15', 20000, 30000, 900103);

-- repair:same-date-low-mileage; repair:range-before
INSERT INTO repairs (id, cost_in_cents, details, station, date, mileage, vehicle_id) VALUES
  (900501, 12500, 'Brake pads', 'EN Garage', '2026-03-25', 10800, 900101),
  (900502, 9900, 'Old repair', 'EN Garage', '2026-02-28', 9800, 900101);

-- inspection:same-date-high-mileage; inspection:en-reminder-plus-three
-- inspection:pl-reminder-plus-seven; inspection:reminder-minus-one
INSERT INTO inspections (id, cost_in_cents, details, station, valid_thru, date, mileage, vehicle_id) VALUES
  (900601, 15000, 'Annual inspection', 'EN Station', '2027-03-25', '2026-03-25', 10900, 900101),
  (900602, 16000, 'Due in three', 'EN Station', '2026-04-18', '2026-03-10', 10300, 900101),
  (900603, 17000, 'Due in seven', 'PL Station', '2026-04-22', '2026-03-12', 4800, 900102),
  (900604, 18000, 'Too early', 'PL Station', '2026-04-21', '2026-03-13', 4900, 900102);

-- insurance:en-reminder-plus-three; insurance:pl-reminder-plus-seven
-- insurance:reminder-plus-one
INSERT INTO insurances (id, cost_in_cents, details, insurer, number, valid_from, valid_thru,
                        date, mileage, insurance_type_id, vehicle_id) VALUES
  (900701, 42000, 'EN policy', 'Insure EN', 'EN-OC-1', '2025-04-18', '2026-04-18',
   '2026-03-05', 10100, 900011, 900101),
  (900702, 43000, 'PL policy', 'Insure PL', 'PL-OC-1', '2025-04-22', '2026-04-22',
   '2026-03-06', 4700, 900011, 900102),
  (900703, 44000, 'Too late', 'Insure EN', 'EN-OC-2', '2025-04-19', '2026-04-19',
   '2026-03-07', 10200, 900011, 900101);

-- routine-service:null-next-date; routine-service:null-next-mileage
-- routine-service:en-reminder-plus-three; routine-service:pl-reminder-plus-seven
INSERT INTO routine_services (id, cost_in_cents, details, next_by_date, next_by_mileage, station,
                              date, mileage, vehicle_id) VALUES
  (900301, 15000, 'No next date', NULL, NULL, 'EN Service', '2026-03-10', 10300, 900101),
  (900302, 20000, 'No next mileage', '2026-05-01', NULL, 'EN Service', '2026-03-20', 10700, 900101),
  (900303, 22000, 'Due in three', '2026-04-18', 12000, 'EN Service', '2026-03-11', 10400, 900101),
  (900304, 23000, 'Due in seven', '2026-04-22', 7000, 'PL Service', '2026-03-14', 4950, 900102);
