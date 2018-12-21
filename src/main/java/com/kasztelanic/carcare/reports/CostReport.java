package com.kasztelanic.carcare.reports;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Locale;
import java.util.function.ToDoubleFunction;

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

import com.kasztelanic.carcare.service.dto.CostResultDto;

@Service
public class CostReport {

    private static final String DECIMAL_FORMAT = "0.00";

    @Autowired
    private MessageSource messageSource;

    public byte[] generateCostReport(Collection<CostResultDto> costs, Locale locale) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(true); ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            DataFormat format = workbook.createDataFormat();
            CellStyle cellStyle = workbook.createCellStyle();
            cellStyle.setDataFormat(format.getFormat(DECIMAL_FORMAT));
            createSheet(workbook, costs, locale, cellStyle);
            workbook.write(os);
            return os.toByteArray();
        }
    }

    private void createSheet(Workbook workbook, Collection<CostResultDto> costs, Locale locale, CellStyle cellStyle) {
        Sheet sheet = workbook.createSheet(messageSource.getMessage("reports.costs", null, locale));
        int rowNum = 0;
        Row titleRow = sheet.createRow(rowNum++);
        String[] titles = { "reports.costs.costs", "reports.costs.insurance", "reports.costs.inspection",
                "reports.costs.service", "reports.costs.repair", "reports.costs.refuel", "reports.costs.sum" };
        for (int i = 0; i < titles.length; i++) {
            Cell cell = titleRow.createCell(i);
            cell.setCellValue(messageSource.getMessage(titles[i], null, locale));
        }
        for (CostResultDto cost : costs) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(String.format("%s %s - %s", cost.getVehicle().getMake(),
                    cost.getVehicle().getModel(), cost.getVehicle().getLicensePlate()));
            createCostCell(1, cost.getInsuranceCosts(), row, cellStyle);
            createCostCell(2, cost.getInspectionCosts(), row, cellStyle);
            createCostCell(3, cost.getRoutineServiceCosts(), row, cellStyle);
            createCostCell(4, cost.getRepairCosts(), row, cellStyle);
            createCostCell(5, cost.getRefuelCosts(), row, cellStyle);
            createCostCell(6, cost.getSum(), row, cellStyle);
        }
        Row sumRow = sheet.createRow(rowNum);
        sumRow.createCell(0).setCellValue(messageSource.getMessage("reports.costs.sum", null, locale));
        createCostCell(1, sumCosts(costs, CostResultDto::getInsuranceCosts), sumRow, cellStyle);
        createCostCell(2, sumCosts(costs, CostResultDto::getInspectionCosts), sumRow, cellStyle);
        createCostCell(3, sumCosts(costs, CostResultDto::getRoutineServiceCosts), sumRow, cellStyle);
        createCostCell(4, sumCosts(costs, CostResultDto::getRepairCosts), sumRow, cellStyle);
        createCostCell(5, sumCosts(costs, CostResultDto::getRefuelCosts), sumRow, cellStyle);
        createCostCell(6, sumCosts(costs, CostResultDto::getSum), sumRow, cellStyle);
        for (int i = 0; i < titles.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createCostCell(int cellNum, double value, Row row, CellStyle cellStyle) {
        Cell cell = row.createCell(cellNum);
        cell.setCellValue(value);
        cell.setCellType(CellType.NUMERIC);
        cell.setCellStyle(cellStyle);
    }

    private static double sumCosts(Collection<CostResultDto> costs, ToDoubleFunction<CostResultDto> extractor) {
        return costs.stream().mapToDouble(extractor::applyAsDouble).sum();
    }
}