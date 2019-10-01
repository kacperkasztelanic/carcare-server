package com.kasztelanic.carcare.service.impl;

import com.kasztelanic.carcare.domain.User;
import com.kasztelanic.carcare.repository.VehicleRepository;
import com.kasztelanic.carcare.service.CostCalculator;
import com.kasztelanic.carcare.service.ReportService;
import com.kasztelanic.carcare.service.dto.CostRequest;
import com.kasztelanic.carcare.service.dto.CostResult;
import com.kasztelanic.carcare.service.dto.PeriodVehicle;
import com.kasztelanic.carcare.service.dto.Report;
import com.kasztelanic.carcare.service.dto.VehicleRichDto;
import com.kasztelanic.carcare.service.exception.ReportGenerationException;
import com.kasztelanic.carcare.service.mapper.VehicleRichMapper;
import com.kasztelanic.carcare.service.reports.CostReport;
import com.kasztelanic.carcare.service.reports.VehicleReport;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.vavr.control.Either;
import io.vavr.control.Try;


@Service
public class ReportServiceImpl implements ReportService {

    private static final String EXTENSION = ".xlsx";

    private final VehicleReport vehicleReport;
    private final CostReport costReport;
    private final VehicleRepository vehicleRepository;
    private final VehicleRichMapper vehicleMapper;
    private final CostCalculator costCalculator;

    @Autowired
    public ReportServiceImpl(VehicleReport vehicleReport, CostReport costReport, VehicleRepository vehicleRepository,
            VehicleRichMapper vehicleMapper, CostCalculator costCalculator) {
        this.vehicleReport = vehicleReport;
        this.costReport = costReport;
        this.vehicleRepository = vehicleRepository;
        this.vehicleMapper = vehicleMapper;
        this.costCalculator = costCalculator;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Either<ReportGenerationException, Report>> generateVehicleReport(long vehicleId, User user) {
        Locale locale = Locale.forLanguageTag(user.getLangKey());
        Function<VehicleRichDto, String> reportNameFunction = v -> v.getLicensePlate()
                .replaceAll("\\s+", "_") + EXTENSION;
        return vehicleRepository.findByIdAndOwnerIsCurrentUser(vehicleId)//
                .map(vehicleMapper::vehicleToVehicleDto)//
                .map(v -> Try.of(() -> vehicleReport.generateVehicleReport(v, locale))//
                        .map(a -> Report.of(reportNameFunction.apply(v), a))//
                        .toEither()//
                        .mapLeft(e -> new ReportGenerationException(
                                "Error generating vehicle report for: " + user.getLogin(), e))//
                );
    }

    @Override
    @Transactional(readOnly = true)
    public Either<ReportGenerationException, Report> generateCostReport(CostRequest costRequest, User user) {
        Locale locale = Locale.forLanguageTag(user.getLangKey());
        List<VehicleRichDto> vehicles = vehicleRepository
                .findAllByIdAndOwnerIsCurrentUser(costRequest.getVehicleIds())//
                .stream()//
                .map(vehicleMapper::vehicleToVehicleDto)//
                .collect(Collectors.toList());
        List<CostResult> costs = vehicles.stream()//
                .map(v -> costCalculator
                        .calculate(PeriodVehicle.of(v.getId(), costRequest.getDateFrom(), costRequest.getDateTo()),
                                v))//
                .collect(Collectors.toList());
        return Try.of(() -> costReport.generateCostReport(costs, vehicles, locale))//
                .toEither()//
                .mapLeft(
                        e -> new ReportGenerationException("Error generating cost report for: " + user.getLogin(), e))//
                .map(a -> Report.of("cost" + EXTENSION, a));
    }
}
