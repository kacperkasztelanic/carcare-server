package com.kasztelanic.carcare.reports;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import com.kasztelanic.carcare.service.dto.VehicleDto;

@Service
public class ReportsService {

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
    }

    private void createMainSheet(Workbook workbook, VehicleDto vehicle, Locale locale) {
        Sheet sheet = workbook.createSheet(messageSource.getMessage("reports.vehicle.main", null, locale));
        createTitleRow(locale, "reports.vehicle.main.title", sheet);
        createRow(vehicle.getMake(), locale, "reports.vehicle.main.make", sheet);
        createRow(vehicle.getModel(), locale, "reports.vehicle.main.model", sheet);
        createRow(vehicle.getFuelType(), locale, "reports.vehicle.main.fuelType", sheet);
        createRow(vehicle.getLicensePlate(), locale, "reports.vehicle.main.licensePlate", sheet);
    }

    private void createTitleRow(Locale locale, String title, Sheet sheet) {
        Row row = sheet.createRow(0);
        Cell cell = row.createCell(0);
        cell.setCellValue(messageSource.getMessage(title, null, locale));
    }

    private void createRow(String value, Locale locale, String title, Sheet sheet) {
        Row row = sheet.createRow(1);
        Cell cell = row.createCell(0);
        cell.setCellValue(messageSource.getMessage(title, null, locale));
        cell = row.createCell(1);
        cell.setCellValue(value);
    }
}
