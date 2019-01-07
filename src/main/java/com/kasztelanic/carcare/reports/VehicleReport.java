package com.kasztelanic.carcare.reports;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormat;
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
import com.kasztelanic.carcare.service.dto.VehicleRichDto;
import com.kasztelanic.carcare.util.DateTimeFormatters;

@Service
public class VehicleReport {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatters.DATE_FORMATTER;
    private static final String DECIMAL_FORMAT = "0.00";

    @Autowired
    private MessageSource messageSource;

    public byte[] generateVehicleReport(VehicleRichDto vehicle, Locale locale) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(true); ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            DataFormat format = workbook.createDataFormat();
            CellStyle cellStyle = workbook.createCellStyle();
            cellStyle.setDataFormat(format.getFormat(DECIMAL_FORMAT));
            createSheets(workbook, vehicle, locale, cellStyle);
            workbook.write(os);
            return os.toByteArray();
        }
    }

    private void createSheets(Workbook workbook, VehicleRichDto vehicle, Locale locale, CellStyle cellStyle) {
        createMainSheet(workbook, vehicle, locale);
        createInsuranceSheet(workbook, vehicle.getInsurance(), locale, cellStyle);
        createInspectionSheet(workbook, vehicle.getInspection(), locale, cellStyle);
        createRoutineServiceSheet(workbook, vehicle.getRoutineService(), locale, cellStyle);
        createRepairSheet(workbook, vehicle.getRepair(), locale, cellStyle);
        createRefuelSheet(workbook, vehicle.getRefuel(), locale, cellStyle);
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
        createMainSheetRow(6, vehicle.getFuelType().getTranslation(), locale, "reports.vehicle.main.fuelType", sheet);
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

    private void createInsuranceSheet(Workbook workbook, Collection<InsuranceDto> insurances, Locale locale,
            CellStyle cellStyle) {
        Sheet sheet = workbook.createSheet(messageSource.getMessage("reports.vehicle.insurance", null, locale));
        int rowNum = 0;
        Row titleRow = sheet.createRow(rowNum++);
        String[] titles = { "reports.vehicle.insurance.date", "reports.vehicle.insurance.mileage",
                "reports.vehicle.insurance.cost", "reports.vehicle.insurance.type", "reports.vehicle.insurance.number",
                "reports.vehicle.insurance.insurer", "reports.vehicle.insurance.validFrom",
                "reports.vehicle.insurance.validThru", "reports.vehicle.insurance.details" };
        for (int i = 0; i < titles.length; i++) {
            Cell cell = titleRow.createCell(i);
            cell.setCellValue(messageSource.getMessage(titles[i], null, locale));
        }

        List<InsuranceDto> sortedInsurances = insurances.stream()
                .sorted(Comparator.comparing(i -> i.getVehicleEvent().getDate())).collect(Collectors.toList());
        for (InsuranceDto insurance : sortedInsurances) {
            Row row = sheet.createRow(rowNum++);
            Cell cell = row.createCell(0);
            cell.setCellValue(FORMATTER.format(insurance.getVehicleEvent().getDate()));
            cell = row.createCell(1);
            cell.setCellValue(insurance.getVehicleEvent().getMileage());
            cell.setCellType(CellType.NUMERIC);
            cell = row.createCell(2);
            cell.setCellValue(insurance.getCostInCents() / 100.0);
            cell.setCellType(CellType.NUMERIC);
            cell.setCellStyle(cellStyle);
            cell = row.createCell(3);
            cell.setCellValue(insurance.getInsuranceType().getTranslation());
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
        for (int i = 0; i < titles.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createInspectionSheet(Workbook workbook, Collection<InspectionDto> inspections, Locale locale,
            CellStyle cellStyle) {
        Sheet sheet = workbook.createSheet(messageSource.getMessage("reports.vehicle.inspection", null, locale));
        int rowNum = 0;
        Row titleRow = sheet.createRow(rowNum++);
        String[] titles = { "reports.vehicle.inspection.date", "reports.vehicle.inspection.mileage",
                "reports.vehicle.inspection.cost", "reports.vehicle.inspection.station",
                "reports.vehicle.inspection.dateNext", "reports.vehicle.inspection.details" };
        for (int i = 0; i < titles.length; i++) {
            Cell cell = titleRow.createCell(i);
            cell.setCellValue(messageSource.getMessage(titles[i], null, locale));
        }

        List<InspectionDto> sortedInspections = inspections.stream()
                .sorted(Comparator.comparing(i -> i.getVehicleEvent().getDate())).collect(Collectors.toList());
        for (InspectionDto inspection : sortedInspections) {
            Row row = sheet.createRow(rowNum++);
            Cell cell = row.createCell(0);
            cell.setCellValue(FORMATTER.format(inspection.getVehicleEvent().getDate()));
            cell = row.createCell(1);
            cell.setCellValue(inspection.getVehicleEvent().getMileage());
            cell.setCellType(CellType.NUMERIC);
            cell = row.createCell(2);
            cell.setCellValue(inspection.getCostInCents() / 100.0);
            cell.setCellType(CellType.NUMERIC);
            cell.setCellStyle(cellStyle);
            cell = row.createCell(3);
            cell.setCellValue(inspection.getStation());
            cell = row.createCell(4);
            cell.setCellValue(FORMATTER.format(inspection.getValidThru()));
            cell = row.createCell(5);
            cell.setCellValue(inspection.getDetails());
        }
        for (int i = 0; i < titles.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createRoutineServiceSheet(Workbook workbook, Set<RoutineServiceDto> routineServices, Locale locale,
            CellStyle cellStyle) {
        Sheet sheet = workbook.createSheet(messageSource.getMessage("reports.vehicle.service", null, locale));
        int rowNum = 0;
        Row titleRow = sheet.createRow(rowNum++);
        String[] titles = { "reports.vehicle.service.date", "reports.vehicle.service.mileage",
                "reports.vehicle.service.cost", "reports.vehicle.service.nextMileage",
                "reports.vehicle.service.nextDate", "reports.vehicle.service.station",
                "reports.vehicle.service.details" };
        for (int i = 0; i < titles.length; i++) {
            Cell cell = titleRow.createCell(i);
            cell.setCellValue(messageSource.getMessage(titles[i], null, locale));
        }

        List<RoutineServiceDto> sortedRoutineServices = routineServices.stream()
                .sorted(Comparator.comparing(i -> i.getVehicleEvent().getDate())).collect(Collectors.toList());
        for (RoutineServiceDto routineService : sortedRoutineServices) {
            Row row = sheet.createRow(rowNum++);
            Cell cell = row.createCell(0);
            cell.setCellValue(FORMATTER.format(routineService.getVehicleEvent().getDate()));
            cell = row.createCell(1);
            cell.setCellValue(routineService.getVehicleEvent().getMileage());
            cell.setCellType(CellType.NUMERIC);
            cell = row.createCell(2);
            cell.setCellValue(routineService.getCostInCents() / 100.0);
            cell.setCellType(CellType.NUMERIC);
            cell.setCellStyle(cellStyle);
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
        for (int i = 0; i < titles.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createRepairSheet(Workbook workbook, Set<RepairDto> repairs, Locale locale, CellStyle cellStyle) {
        Sheet sheet = workbook.createSheet(messageSource.getMessage("reports.vehicle.repair", null, locale));
        int rowNum = 0;
        Row titleRow = sheet.createRow(rowNum++);
        String[] titles = { "reports.vehicle.repair.date", "reports.vehicle.repair.mileage",
                "reports.vehicle.repair.cost", "reports.vehicle.repair.station", "reports.vehicle.repair.details" };
        for (int i = 0; i < titles.length; i++) {
            Cell cell = titleRow.createCell(i);
            cell.setCellValue(messageSource.getMessage(titles[i], null, locale));
        }

        List<RepairDto> sortedRepairs = repairs.stream()
                .sorted(Comparator.comparing(i -> i.getVehicleEvent().getDate())).collect(Collectors.toList());
        for (RepairDto repairDto : sortedRepairs) {
            Row row = sheet.createRow(rowNum++);
            Cell cell = row.createCell(0);
            cell.setCellValue(FORMATTER.format(repairDto.getVehicleEvent().getDate()));
            cell = row.createCell(1);
            cell.setCellValue(repairDto.getVehicleEvent().getMileage());
            cell.setCellType(CellType.NUMERIC);
            cell = row.createCell(2);
            cell.setCellValue(repairDto.getCostInCents() / 100.0);
            cell.setCellType(CellType.NUMERIC);
            cell.setCellStyle(cellStyle);
            cell = row.createCell(3);
            cell.setCellValue(repairDto.getStation());
            cell = row.createCell(4);
            cell.setCellValue(repairDto.getDetails());
        }
        for (int i = 0; i < titles.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createRefuelSheet(Workbook workbook, Set<RefuelDto> refuels, Locale locale, CellStyle cellStyle) {
        Sheet sheet = workbook.createSheet(messageSource.getMessage("reports.vehicle.refuel", null, locale));
        int rowNum = 0;
        Row titleRow = sheet.createRow(rowNum++);
        String[] titles = { "reports.vehicle.refuel.date", "reports.vehicle.refuel.mileage",
                "reports.vehicle.refuel.cost", "reports.vehicle.refuel.volume", "reports.vehicle.refuel.price",
                "reports.vehicle.refuel.station" };
        for (int i = 0; i < titles.length; i++) {
            Cell cell = titleRow.createCell(i);
            cell.setCellValue(messageSource.getMessage(titles[i], null, locale));
        }

        List<RefuelDto> sortedRefuels = refuels.stream()
                .sorted(Comparator.comparing(i -> i.getVehicleEvent().getDate())).collect(Collectors.toList());
        for (RefuelDto refuelDto : sortedRefuels) {
            Row row = sheet.createRow(rowNum++);
            Cell cell = row.createCell(0);
            cell.setCellValue(FORMATTER.format(refuelDto.getVehicleEvent().getDate()));
            cell = row.createCell(1);
            cell.setCellValue(refuelDto.getVehicleEvent().getMileage());
            cell.setCellType(CellType.NUMERIC);
            cell = row.createCell(2);
            cell.setCellValue(refuelDto.getCostInCents() / 100.0);
            cell.setCellType(CellType.NUMERIC);
            cell.setCellStyle(cellStyle);
            cell = row.createCell(3);
            cell.setCellValue(refuelDto.getVolume() / 1000.0);
            cell.setCellStyle(cellStyle);
            cell.setCellType(CellType.NUMERIC);
            cell = row.createCell(4);
            cell.setCellValue(refuelDto.getCostInCents() * 10.0 / refuelDto.getVolume());
            cell.setCellStyle(cellStyle);
            cell.setCellType(CellType.NUMERIC);
            cell = row.createCell(5);
            cell.setCellValue(refuelDto.getStation());
        }
        for (int i = 0; i < titles.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }
}
