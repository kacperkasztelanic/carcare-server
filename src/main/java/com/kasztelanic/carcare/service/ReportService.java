package com.kasztelanic.carcare.service;

import com.kasztelanic.carcare.domain.User;
import com.kasztelanic.carcare.service.dto.CostRequest;
import com.kasztelanic.carcare.service.dto.Report;
import com.kasztelanic.carcare.service.exception.ReportGenerationException;
import io.vavr.control.Either;

import java.util.Optional;

public interface ReportService {

    Optional<Either<ReportGenerationException, Report>> generateVehicleReport(long vehicleId, User user);

    Either<ReportGenerationException, Report> generateCostReport(CostRequest costRequest, User user);
}
