package com.kasztelanic.carcare.reports;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import com.kasztelanic.carcare.service.dto.InspectionDto;
import com.kasztelanic.carcare.service.dto.InsuranceDto;
import com.kasztelanic.carcare.service.dto.RefuelDto;
import com.kasztelanic.carcare.service.dto.RepairDto;
import com.kasztelanic.carcare.service.dto.RoutineServiceDto;
import com.kasztelanic.carcare.service.dto.VehicleDto;
import com.kasztelanic.carcare.util.DateTimeFormatters;

@Service
public class ReportsService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatters.DATE_FORMATTER;

    @Autowired
    private MessageSource messageSource;

    public byte[] generateVehicleReport(VehicleDto vehicle, Locale locale) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(true); ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            createSheets(workbook, vehicle, locale);
            workbook.write(os);
            return os.toByteArray();
        }
    }

    private void createSheets(Workbook workbook, VehicleDto vehicle, Locale locale) {
        createMainSheet(workbook, vehicle, locale);
        createInsuranceSheet(workbook, vehicle.getInsurance(), locale);
        createInspectionSheet(workbook, vehicle.getInspection(), locale);
        createRoutineServiceSheet(workbook, vehicle.getRoutineService(), locale);
        createRepairSheet(workbook, vehicle.getRepair(), locale);
        createRefuelSheet(workbook, vehicle.getRefuel(), locale);
    }

    private void createMainSheet(Workbook workbook, VehicleDto vehicle, Locale locale) {
        Sheet sheet = workbook.createSheet(messageSource.getMessage("reports.vehicle.main", null, locale));
        createMainSheetTitleRow(locale, "reports.vehicle.main.title", sheet);
        createMainSheetRow(1, vehicle.getMake(), locale, "reports.vehicle.main.make", sheet);
        createMainSheetRow(2, vehicle.getModel(), locale, "reports.vehicle.main.model", sheet);
        createMainSheetRow(3, vehicle.getVehicleDetails().getModelSuffix(), locale, "reports.vehicle.main.suffix",
                sheet);
        createMainSheetRow(4, vehicle.getLicensePlate(), locale, "reports.vehicle.main.licensePlate", sheet);
        createMainSheetNumericRow(5, vehicle.getVehicleDetails().getYearOfManufacture(), locale,
                "reports.vehicle.main.year", sheet);
        createMainSheetRow(6, vehicle.getFuelType(), locale, "reports.vehicle.main.fuelType", sheet);
        createMainSheetNumericRow(7, vehicle.getVehicleDetails().getEngineVolume(), locale,
                "reports.vehicle.main.volume", sheet);
        createMainSheetNumericRow(8, vehicle.getVehicleDetails().getEnginePower(), locale, "reports.vehicle.main.power",
                sheet);
        createMainSheetNumericRow(9, vehicle.getVehicleDetails().getWeight(), locale, "reports.vehicle.main.weight",
                sheet);
        createMainSheetRow(10, vehicle.getVehicleDetails().getVinNumber(), locale, "reports.vehicle.main.vin", sheet);
        createMainSheetRow(11, vehicle.getVehicleDetails().getVehicleCard(), locale, "reports.vehicle.main.card",
                sheet);
        createMainSheetRow(12, vehicle.getVehicleDetails().getRegistrationCertificate(), locale,
                "reports.vehicle.main.certificate", sheet);
        for (int i = 0; i < 2; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createMainSheetTitleRow(Locale locale, String title, Sheet sheet) {
        Row row = sheet.createRow(0);
        Cell cell = row.createCell(0);
        cell.setCellValue(messageSource.getMessage(title, null, locale));
    }

    private void createMainSheetRow(int rowNum, String value, Locale locale, String title, Sheet sheet) {
        Row row = sheet.createRow(rowNum);
        Cell cell = row.createCell(0);
        cell.setCellValue(messageSource.getMessage(title, null, locale));
        cell = row.createCell(1);
        cell.setCellValue(value);
    }

    private void createMainSheetNumericRow(int rowNum, Number value, Locale locale, String title, Sheet sheet) {
        Row row = sheet.createRow(rowNum);
        Cell cell = row.createCell(0);
        cell.setCellValue(messageSource.getMessage(title, null, locale));
        cell = row.createCell(1);
        cell.setCellValue(value.doubleValue());
        cell.setCellType(CellType.NUMERIC);
    }

    private void createInsuranceSheet(Workbook workbook, Collection<InsuranceDto> insurances, Locale locale) {
        Sheet sheet = workbook.createSheet(messageSource.getMessage("reports.vehicle.insurance", null, locale));
        Row titleRow = sheet.createRow(0);
        titleRow.createCell(0).setCellValue(messageSource.getMessage("reports.vehicle.insurance.date", null, locale));
        titleRow.createCell(1)
                .setCellValue(messageSource.getMessage("reports.vehicle.insurance.mileage", null, locale));
        titleRow.createCell(2).setCellValue(messageSource.getMessage("reports.vehicle.insurance.cost", null, locale));
        titleRow.createCell(3).setCellValue(messageSource.getMessage("reports.vehicle.insurance.type", null, locale));
        titleRow.createCell(4).setCellValue(messageSource.getMessage("reports.vehicle.insurance.number", null, locale));
        titleRow.createCell(5)
                .setCellValue(messageSource.getMessage("reports.vehicle.insurance.insurer", null, locale));
        titleRow.createCell(6)
                .setCellValue(messageSource.getMessage("reports.vehicle.insurance.validFrom", null, locale));
        titleRow.createCell(7)
                .setCellValue(messageSource.getMessage("reports.vehicle.insurance.validThru", null, locale));
        titleRow.createCell(8)
                .setCellValue(messageSource.getMessage("reports.vehicle.insurance.details", null, locale));
        int rownNum = 1;
        List<InsuranceDto> sortedInsurances = insurances.stream()
                .sorted((a, b) -> a.getVehicleEvent().getDate().compareTo(b.getVehicleEvent().getDate()))
                .collect(Collectors.toList());
        for (InsuranceDto insurance : sortedInsurances) {
            Row row = sheet.createRow(rownNum++);
            Cell cell = row.createCell(0);
            cell.setCellValue(FORMATTER.format(insurance.getVehicleEvent().getDate()));
            cell = row.createCell(1);
            cell.setCellValue(insurance.getVehicleEvent().getMileage());
            cell.setCellType(CellType.NUMERIC);
            cell = row.createCell(2);
            cell.setCellValue(String.format("%.2f", insurance.getCostInCents() / 100.0));
            cell.setCellType(CellType.NUMERIC);
            cell = row.createCell(3);
            cell.setCellValue(insurance.getInsuranceType());
            cell = row.createCell(4);
            cell.setCellValue(insurance.getNumber());
            cell = row.createCell(5);
            cell.setCellValue(insurance.getInsurer());
            cell = row.createCell(6);
            cell.setCellValue(FORMATTER.format(insurance.getValidFrom()));
            cell = row.createCell(7);
            cell.setCellValue(FORMATTER.format(insurance.getValidThru()));
            cell = row.createCell(8);
            cell.setCellValue(insurance.getDetails());
        }
        for (int i = 0; i < 9; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createInspectionSheet(Workbook workbook, Collection<InspectionDto> inspections, Locale locale) {
        Sheet sheet = workbook.createSheet(messageSource.getMessage("reports.vehicle.inspection", null, locale));
        Row titleRow = sheet.createRow(0);
        titleRow.createCell(0).setCellValue(messageSource.getMessage("reports.vehicle.inspection.date", null, locale));
        titleRow.createCell(1)
                .setCellValue(messageSource.getMessage("reports.vehicle.inspection.mileage", null, locale));
        titleRow.createCell(2).setCellValue(messageSource.getMessage("reports.vehicle.inspection.cost", null, locale));
        titleRow.createCell(3)
                .setCellValue(messageSource.getMessage("reports.vehicle.inspection.station", null, locale));
        titleRow.createCell(4)
                .setCellValue(messageSource.getMessage("reports.vehicle.inspection.dateNext", null, locale));
        titleRow.createCell(5)
                .setCellValue(messageSource.getMessage("reports.vehicle.inspection.details", null, locale));

        int rownNum = 1;
        List<InspectionDto> sortedInspections = inspections.stream()
                .sorted((a, b) -> a.getVehicleEvent().getDate().compareTo(b.getVehicleEvent().getDate()))
                .collect(Collectors.toList());
        for (InspectionDto inspection : sortedInspections) {
            Row row = sheet.createRow(rownNum++);
            Cell cell = row.createCell(0);
            cell.setCellValue(FORMATTER.format(inspection.getVehicleEvent().getDate()));
            cell = row.createCell(1);
            cell.setCellValue(inspection.getVehicleEvent().getMileage());
            cell.setCellType(CellType.NUMERIC);
            cell = row.createCell(2);
            cell.setCellValue(String.format("%.2f", inspection.getCostInCents() / 100.0));
            cell.setCellType(CellType.NUMERIC);
            cell = row.createCell(3);
            cell.setCellValue(inspection.getStation());
            cell = row.createCell(4);
            cell.setCellValue(FORMATTER.format(inspection.getValidThru()));
            cell = row.createCell(5);
            cell.setCellValue(inspection.getDetails());
        }
        for (int i = 0; i < 6; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createRoutineServiceSheet(Workbook workbook, Set<RoutineServiceDto> routineServices, Locale locale) {
        Sheet sheet = workbook.createSheet(messageSource.getMessage("reports.vehicle.service", null, locale));
        Row titleRow = sheet.createRow(0);
        titleRow.createCell(0).setCellValue(messageSource.getMessage("reports.vehicle.service.date", null, locale));
        titleRow.createCell(1).setCellValue(messageSource.getMessage("reports.vehicle.service.mileage", null, locale));
        titleRow.createCell(2).setCellValue(messageSource.getMessage("reports.vehicle.service.cost", null, locale));
        titleRow.createCell(3)
                .setCellValue(messageSource.getMessage("reports.vehicle.service.nextMileage", null, locale));
        titleRow.createCell(4).setCellValue(messageSource.getMessage("reports.vehicle.service.nextDate", null, locale));
        titleRow.createCell(5).setCellValue(messageSource.getMessage("reports.vehicle.service.station", null, locale));
        titleRow.createCell(6).setCellValue(messageSource.getMessage("reports.vehicle.service.details", null, locale));

        int rownNum = 1;
        List<RoutineServiceDto> sortedRoutineServices = routineServices.stream()
                .sorted((a, b) -> a.getVehicleEvent().getDate().compareTo(b.getVehicleEvent().getDate()))
                .collect(Collectors.toList());
        for (RoutineServiceDto routineService : sortedRoutineServices) {
            Row row = sheet.createRow(rownNum++);
            Cell cell = row.createCell(0);
            cell.setCellValue(FORMATTER.format(routineService.getVehicleEvent().getDate()));
            cell = row.createCell(1);
            cell.setCellValue(routineService.getVehicleEvent().getMileage());
            cell.setCellType(CellType.NUMERIC);
            cell = row.createCell(2);
            cell.setCellValue(String.format("%.2f", routineService.getCostInCents() / 100.0));
            cell.setCellType(CellType.NUMERIC);
            cell = row.createCell(3);
            cell.setCellValue(routineService.getNextByMileage());
            cell.setCellType(CellType.NUMERIC);
            cell = row.createCell(4);
            cell.setCellValue(FORMATTER.format(routineService.getNextByDate()));
            cell = row.createCell(5);
            cell.setCellValue(routineService.getStation());
            cell = row.createCell(6);
            cell.setCellValue(routineService.getDetails());
        }
        for (int i = 0; i < 7; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createRepairSheet(Workbook workbook, Set<RepairDto> repairs, Locale locale) {
        Sheet sheet = workbook.createSheet(messageSource.getMessage("reports.vehicle.repair", null, locale));
        Row titleRow = sheet.createRow(0);
        titleRow.createCell(0).setCellValue(messageSource.getMessage("reports.vehicle.repair.date", null, locale));
        titleRow.createCell(1).setCellValue(messageSource.getMessage("reports.vehicle.repair.mileage", null, locale));
        titleRow.createCell(2).setCellValue(messageSource.getMessage("reports.vehicle.repair.cost", null, locale));
        titleRow.createCell(3).setCellValue(messageSource.getMessage("reports.vehicle.repair.station", null, locale));
        titleRow.createCell(4).setCellValue(messageSource.getMessage("reports.vehicle.repair.details", null, locale));

        int rownNum = 1;
        List<RepairDto> sortedRepairs = repairs.stream()
                .sorted((a, b) -> a.getVehicleEvent().getDate().compareTo(b.getVehicleEvent().getDate()))
                .collect(Collectors.toList());
        for (RepairDto repairDto : sortedRepairs) {
            Row row = sheet.createRow(rownNum++);
            Cell cell = row.createCell(0);
            cell.setCellValue(FORMATTER.format(repairDto.getVehicleEvent().getDate()));
            cell = row.createCell(1);
            cell.setCellValue(repairDto.getVehicleEvent().getMileage());
            cell.setCellType(CellType.NUMERIC);
            cell = row.createCell(2);
            cell.setCellValue(String.format("%.2f", repairDto.getCostInCents() / 100.0));
            cell.setCellType(CellType.NUMERIC);
            cell = row.createCell(3);
            cell.setCellValue(repairDto.getStation());
            cell = row.createCell(4);
            cell.setCellValue(repairDto.getDetails());
        }
        for (int i = 0; i < 5; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createRefuelSheet(Workbook workbook, Set<RefuelDto> refuels, Locale locale) {
        Sheet sheet = workbook.createSheet(messageSource.getMessage("reports.vehicle.refuel", null, locale));
        Row titleRow = sheet.createRow(0);
        titleRow.createCell(0).setCellValue(messageSource.getMessage("reports.vehicle.refuel.date", null, locale));
        titleRow.createCell(1).setCellValue(messageSource.getMessage("reports.vehicle.refuel.mileage", null, locale));
        titleRow.createCell(2).setCellValue(messageSource.getMessage("reports.vehicle.refuel.cost", null, locale));
        titleRow.createCell(3).setCellValue(messageSource.getMessage("reports.vehicle.refuel.volume", null, locale));
        titleRow.createCell(4).setCellValue(messageSource.getMessage("reports.vehicle.refuel.station", null, locale));

        int rownNum = 1;
        List<RefuelDto> sortedRefuels = refuels.stream()
                .sorted((a, b) -> a.getVehicleEvent().getDate().compareTo(b.getVehicleEvent().getDate()))
                .collect(Collectors.toList());
        for (RefuelDto refuelDto : sortedRefuels) {
            Row row = sheet.createRow(rownNum++);
            Cell cell = row.createCell(0);
            cell.setCellValue(FORMATTER.format(refuelDto.getVehicleEvent().getDate()));
            cell = row.createCell(1);
            cell.setCellValue(refuelDto.getVehicleEvent().getMileage());
            cell.setCellType(CellType.NUMERIC);
            cell = row.createCell(2);
            cell.setCellValue(String.format("%.2f", refuelDto.getCostInCents() / 100.0));
            cell.setCellType(CellType.NUMERIC);
            cell = row.createCell(3);
            cell.setCellValue(String.format("%.2f", refuelDto.getVolume() / 1000.0));
            cell.setCellType(CellType.NUMERIC);
            cell = row.createCell(4);
            cell.setCellValue(refuelDto.getStation());
        }
        for (int i = 0; i < 5; i++) {
            sheet.autoSizeColumn(i);
        }
    }
}
