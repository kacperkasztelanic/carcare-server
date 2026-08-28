package com.kasztelanic.carcare.golden;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GoldenReferenceTest {

    private static final Map<String, Long> HANDLES = Map.of("vehicle:en-primary", 412L);
    private static final Map<String, String> HEADERS = Map.of(
        "Content-Type", "application/json",
        "Cache-Control", "no-cache, no-store, max-age=0, must-revalidate"
    );

    @Test
    void resolvesLiveVehicleIdAndComparesFixedPrecisionJsonValues() {
        GoldenReference reference = GoldenReference.load("golden/stats/consumption-period-en.json");
        byte[] body = """
            {
              "periodVehicle": {
                "vehicleId": 412,
                "dateFrom": "2026-03-01",
                "dateTo": "2026-03-31"
              },
              "volume": 87.0,
              "mileage": 1000,
              "averageConsumption": 8.7
            }
            """.getBytes(StandardCharsets.UTF_8);

        GoldenReference.Comparison comparison = reference.compareJson(200, HEADERS, body, HANDLES);

        assertThat(comparison.matches()).isTrue();
        assertThat(comparison.message()).contains("matches");
    }

    @Test
    void namesTheFirstDifferingJsonPathInItsFailureMessage() {
        GoldenReference reference = GoldenReference.load("golden/stats/consumption-period-en.json");
        byte[] body = """
            {
              "periodVehicle": {
                "vehicleId": 412,
                "dateFrom": "2026-03-01",
                "dateTo": "2026-03-31"
              },
              "volume": 87.1,
              "mileage": 1000,
              "averageConsumption": 8.7
            }
            """.getBytes(StandardCharsets.UTF_8);

        GoldenReference.Comparison comparison = reference.compareJson(200, HEADERS, body, HANDLES);

        assertThat(comparison.matches()).isFalse();
        assertThat(comparison.message()).contains("$.body.volume").contains("87.000000").contains("87.100000");
    }

    @Test
    void namesTheFirstDifferingWorkbookCellInItsFailureMessage() throws Exception {
        byte[] workbookBytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet(WorkbookValues.COST_SHEET_NAME);
            String[] headers = {"Costs (PLN)", "Insurance", "Inspection", "Routine Service", "Repair", "Refuel", "Sum"};
            var headerRow = sheet.createRow(0);
            for (int column = 0; column < headers.length; column++) {
                headerRow.createCell(column).setCellValue(headers[column]);
            }
            CellStyle money = workbook.createCellStyle();
            money.setDataFormat(workbook.createDataFormat().getFormat("0.00"));
            addCostRow(sheet, 1, money, "Ford Focus - EN 1001", 860, 310, 570, 125, 770, 2635);
            addCostRow(sheet, 2, money, "Mazda Three - EN 1003", 0, 0, 0, 0, 180, 180);
            addCostRow(sheet, 3, money, "Sum", 860, 310, 570, 125, 950, 2815);
            sheet.getRow(2).getCell(1).setCellValue(0.01);
            sheet.getRow(2).getCell(1).setCellStyle(money);
            workbook.write(output);
            workbookBytes = output.toByteArray();
        }

        GoldenReference reference = GoldenReference.load("golden/reports/costs-en.json");
        GoldenReference.Comparison comparison = reference.compareWorkbook(200, Map.of(
            "Content-Type", "application/vnd.ms-excel",
            "Content-Disposition", "form-data; name=\"cost.xlsx\"; filename=\"cost.xlsx\"",
            "Cache-Control", "must-revalidate, post-check=0, pre-check=0"
        ), workbookBytes, Map.of("vehicle:en-primary", 412L, "vehicle:zero-consumption", 413L));

        assertThat(comparison.matches()).isFalse();
        assertThat(comparison.message()).contains("$.body.sheets[0].rows[2][1]")
            .contains("0.00").contains("0.01");
    }

    private static void addCostRow(org.apache.poi.ss.usermodel.Sheet sheet, int rowIndex, CellStyle money,
                                   String vehicle, double insurance, double inspection, double routineService,
                                   double repair, double refuel, double sum) {
        var row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(vehicle);
        double[] values = {insurance, inspection, routineService, repair, refuel, sum};
        for (int column = 0; column < values.length; column++) {
            var cell = row.createCell(column + 1);
            cell.setCellStyle(money);
            cell.setCellValue(values[column]);
        }
    }
}
