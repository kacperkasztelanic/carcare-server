package com.kasztelanic.carcare.golden;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoldenReferenceTest {

    private static final Map<String, Long> HANDLES = Map.of("vehicle:en-primary", 412L);
    private static final Map<String, String> HEADERS = Map.of(
        "Content-Type", "application/json",
        "Cache-Control", "no-cache, no-store, max-age=0, must-revalidate"
    );
    private static final String RAW_BODY_GOLDEN = "golden/stats/consumption-period-zero.json";
    private static final String RAW_BODY_HANDLE = "vehicle:zero-consumption";
    private static final Map<String, String> PROBLEM_HEADERS = Map.of(
        "Content-Type", "application/problem+json",
        "Cache-Control", "no-cache, no-store, max-age=0, must-revalidate"
    );

    @Test
    void resolvesLiveVehicleIdAndComparesFixedPrecisionJsonValues() {
        GoldenReference reference = GoldenReference.load("golden/stats/consumption-period-en.json");
        byte[] body = consumptionPeriodBody(412);

        GoldenReference.Comparison comparison = reference.compareJson(200, HEADERS, body, HANDLES);

        assertThat(comparison.matches()).isTrue();
        assertThat(comparison.message()).contains("matches");
    }

    @Test
    void acceptsCrossTableIdCollisionsOutsideTheVehicleNamespace() {
        GoldenReference reference = GoldenReference.load("golden/stats/consumption-period-en.json");
        Map<String, Long> handles = Map.of(
            "vehicle:en-primary", 412L,
            "fuel-type:diesel", 412L,
            "insurance-type:oc", 412L
        );

        GoldenReference.Comparison comparison = reference.compareJson(200, HEADERS, consumptionPeriodBody(412), handles);

        assertThat(comparison.matches()).isTrue();
    }

    @Test
    void rejectsCollidingVehicleHandles() {
        GoldenReference reference = GoldenReference.load("golden/stats/consumption-period-en.json");
        Map<String, Long> handles = Map.of(
            "vehicle:en-primary", 412L,
            "vehicle:pl-primary", 412L
        );

        assertThatThrownBy(() -> reference.compareJson(200, HEADERS, consumptionPeriodBody(412), handles))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("vehicle:en-primary")
            .hasMessageContaining("vehicle:pl-primary");
    }

    @Test
    void rawHandleReplacementGuardsAnIdEmbeddedInALongerNumber() {
        // 26 appears inside "2026-03-31" flanked by digits, so the lookarounds suppress it and
        // only the standalone vehicleId is rewritten.
        GoldenReference reference = GoldenReference.load(RAW_BODY_GOLDEN);

        GoldenReference.Comparison comparison = reference.compareJson(
            500, PROBLEM_HEADERS, rawBodyWithVehicleId(26), Map.of(RAW_BODY_HANDLE, 26L));

        assertThat(comparison.matches()).isTrue();
    }

    @Test
    void rawHandleReplacementDoesNotGuardAnIdDelimitedByNonDigits() {
        // Pins a known limitation rather than an intended behaviour: 31 in "2026-03-31" is flanked
        // by '-' and '"', so (?<!\d)...(?!\d) lets it through and the date is corrupted. The same
        // holds for an id of 500 against "error.http.500". The raw path has no golden consuming it
        // today; anchoring the replacement to the "vehicleId" field is the fix if one ever does.
        GoldenReference reference = GoldenReference.load(RAW_BODY_GOLDEN);

        GoldenReference.Comparison comparison = reference.compareJson(
            500, PROBLEM_HEADERS, rawBodyWithVehicleId(31), Map.of(RAW_BODY_HANDLE, 31L));

        assertThat(comparison.matches()).isFalse();
        assertThat(comparison.message()).contains("mismatch at");
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

    /** Rebuilds the captured raw body with a concrete vehicle id in place of its handle. */
    private static byte[] rawBodyWithVehicleId(long vehicleId) {
        try (InputStream stream = Thread.currentThread().getContextClassLoader()
            .getResourceAsStream(RAW_BODY_GOLDEN)) {
            String capturedBody = new ObjectMapper().readTree(stream).path("body").asText();
            return capturedBody.replace(RAW_BODY_HANDLE, Long.toString(vehicleId))
                .getBytes(StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not read " + RAW_BODY_GOLDEN, exception);
        }
    }

    private static byte[] consumptionPeriodBody(long vehicleId) {
        return ("""
            {
              "periodVehicle": {
                "vehicleId": %d,
                "dateFrom": "2026-03-01",
                "dateTo": "2026-03-31"
              },
              "volume": 87.0,
              "mileage": 1000,
              "averageConsumption": 8.7
            }
            """.formatted(vehicleId)).getBytes(StandardCharsets.UTF_8);
    }
}
