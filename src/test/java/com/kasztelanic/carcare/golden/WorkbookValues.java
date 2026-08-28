package com.kasztelanic.carcare.golden;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reduces a report workbook to the value-level representation committed under {@code golden/}.
 *
 * <p>The extractor deliberately reads POI's raw cell values.  {@code DataFormatter} is not used:
 * display-localised values are not part of the report contract, while the cell type, stored value,
 * and style format are.</p>
 */
public final class WorkbookValues {

    public static final String COST_SHEET_NAME = "Costs";
    private static final String MONEY_FORMAT = "0.00";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WorkbookValues() {
    }

    /**
     * Controls whether the unordered per-vehicle rows in the cost report are normalised.
     */
    public enum SortPolicy {
        /** Sort sheets named {@value #COST_SHEET_NAME}, as the Phase 3 capture did. */
        AUTO,
        /** Sort only the cost-report rows. */
        COST_REPORT,
        /** Preserve every row in workbook order. */
        NATURAL
    }

    /** A single workbook cell in the canonical golden representation. */
    public record CellValue(String type, String value, String dataFormat) {
    }

    /** A worksheet in workbook order. */
    public record SheetValues(String name, List<List<CellValue>> rows) {
        public SheetValues {
            rows = rows.stream().map(List::copyOf).toList();
        }
    }

    /** The complete canonical workbook body. */
    public record Document(List<SheetValues> sheets) {
        public Document {
            sheets = List.copyOf(sheets);
        }

        public JsonNode asJson() {
            return MAPPER.valueToTree(this);
        }
    }

    /**
     * Extracts a workbook using the same automatic policy used by the committed capture: only the
     * {@code Costs} sheet has its middle (per-vehicle) rows sorted.
     */
    public static Document extract(byte[] workbookBytes) throws IOException {
        return extract(workbookBytes, SortPolicy.AUTO);
    }

    /** Extracts a workbook with an explicitly selected row-order policy. */
    public static Document extract(byte[] workbookBytes, SortPolicy sortPolicy) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(workbookBytes))) {
            List<SheetValues> sheets = new ArrayList<>();
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                List<List<CellValue>> rows = new ArrayList<>();
                for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    List<CellValue> cells = new ArrayList<>();
                    if (row != null) {
                        for (int cellIndex = 0; cellIndex < row.getLastCellNum(); cellIndex++) {
                            cells.add(reduceCell(row.getCell(cellIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)));
                        }
                    }
                    rows.add(List.copyOf(cells));
                }
                if (shouldSort(sheet.getSheetName(), sortPolicy)) {
                    sortCostRows(rows);
                }
                sheets.add(new SheetValues(sheet.getSheetName(), rows));
            }
            return new Document(sheets);
        }
    }

    private static boolean shouldSort(String sheetName, SortPolicy sortPolicy) {
        return COST_SHEET_NAME.equals(sheetName)
            && (sortPolicy == SortPolicy.COST_REPORT || sortPolicy == SortPolicy.AUTO);
    }

    private static CellValue reduceCell(Cell cell) {
        if (cell == null) {
            return new CellValue(CellType.BLANK.name(), null, "General");
        }

        String dataFormat = cell.getCellStyle().getDataFormatString();
        String value;
        CellType type = cell.getCellType();
        switch (type) {
            case STRING -> value = cell.getStringCellValue();
            case NUMERIC -> value = decimal(cell.getNumericCellValue(), isMoney(dataFormat) ? 2 : 6);
            case BOOLEAN -> value = Boolean.toString(cell.getBooleanCellValue());
            case ERROR -> value = errorValue(cell.getErrorCellValue());
            case FORMULA -> value = cell.getCellFormula();
            case BLANK -> value = null;
            default -> throw new IllegalStateException("Unsupported POI cell type: " + type);
        }
        return new CellValue(type.name(), value, dataFormat);
    }

    private static String errorValue(byte errorCode) {
        String error = FormulaError.forInt(errorCode).getString();
        return "#DIV/0!".equals(error) ? "Infinity" : error;
    }

    private static boolean isMoney(String dataFormat) {
        return MONEY_FORMAT.equals(dataFormat);
    }

    private static String decimal(double value, int scale) {
        if (Double.isNaN(value)) {
            return "NaN";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "Infinity" : "-Infinity";
        }
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }

    private static void sortCostRows(List<List<CellValue>> rows) {
        if (rows.size() <= 2) {
            return;
        }
        List<List<CellValue>> middle = new ArrayList<>(rows.subList(1, rows.size() - 1));
        middle.sort(Comparator.comparing(WorkbookValues::sortKey));
        for (int index = 0; index < middle.size(); index++) {
            rows.set(index + 1, middle.get(index));
        }
    }

    private static String sortKey(List<CellValue> row) {
        if (row.isEmpty()) {
            return "";
        }
        return String.valueOf(row.get(0).value());
    }
}
