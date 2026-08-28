package com.kasztelanic.carcare.golden;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorkbookValuesTest {

    @Test
    void extractsRawValuesWithMoneyPrecisionAndSortsOnlyCostRows() throws Exception {
        byte[] workbookBytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet(WorkbookValues.COST_SHEET_NAME);
            sheet.createRow(0).createCell(0).setCellValue("Costs (PLN)");
            sheet.createRow(1).createCell(0).setCellValue("Zulu");
            sheet.createRow(2).createCell(0).setCellValue("Alpha");
            Cell money = sheet.getRow(1).createCell(1);
            CellStyle moneyStyle = workbook.createCellStyle();
            moneyStyle.setDataFormat(workbook.createDataFormat().getFormat("0.00"));
            money.setCellStyle(moneyStyle);
            money.setCellValue(12.5);
            sheet.getRow(2).createCell(1).setCellValue(1.234567);
            sheet.createRow(3).createCell(0).setCellValue("Sum");
            workbook.write(output);
            workbookBytes = output.toByteArray();
        }

        WorkbookValues.Document document = WorkbookValues.extract(workbookBytes);
        List<List<WorkbookValues.CellValue>> rows = document.sheets().get(0).rows();

        assertThat(rows).hasSize(4);
        assertThat(rows.get(1).get(0).value()).isEqualTo("Alpha");
        assertThat(rows.get(1).get(1).value()).isEqualTo("1.234567");
        assertThat(rows.get(2).get(0).value()).isEqualTo("Zulu");
        assertThat(rows.get(2).get(1).value()).isEqualTo("12.50");
        assertThat(rows.get(2).get(1).dataFormat()).isEqualTo("0.00");
    }

    @Test
    void emitsInfinitySentinelForExcelDivisionErrorAndKeepsNaturalVehicleOrder() throws Exception {
        byte[] workbookBytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Refuel");
            sheet.createRow(0).createCell(0).setCellValue("first");
            Cell error = sheet.createRow(1).createCell(0);
            error.setCellErrorValue(FormulaError.DIV0.getCode());
            workbook.write(output);
            workbookBytes = output.toByteArray();
        }

        WorkbookValues.Document document = WorkbookValues.extract(workbookBytes);

        assertThat(document.sheets()).extracting(WorkbookValues.SheetValues::name).containsExactly("Refuel");
        assertThat(document.sheets().get(0).rows().get(1).get(0))
            .isEqualTo(new WorkbookValues.CellValue("ERROR", "Infinity", "General"));
    }
}
