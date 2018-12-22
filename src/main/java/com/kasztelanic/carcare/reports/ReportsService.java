package com.kasztelanic.carcare.reports;

import java.io.IOException;
import java.util.Collection;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.kasztelanic.carcare.service.dto.CostResult;
import com.kasztelanic.carcare.service.dto.VehicleDto;

@Service
public class ReportsService {

    @Autowired
    private VehicleReport vehicleReport;

    @Autowired
    private CostReport costReport;

    public byte[] generateVehicleReport(VehicleDto vehicle, Locale locale) throws IOException {
        return vehicleReport.generateVehicleReport(vehicle, locale);
    }

    public byte[] generateCostReport(Collection<CostResult> costs, Collection<VehicleDto> vehicles, Locale locale)
            throws IOException {
        return costReport.generateCostReport(costs, vehicles, locale);
    }
}
