package com.kasztelanic.carcare.web.rest;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kasztelanic.carcare.domain.User;
import com.kasztelanic.carcare.reports.ReportsService;
import com.kasztelanic.carcare.repository.VehicleRepository;
import com.kasztelanic.carcare.service.CostCalculator;
import com.kasztelanic.carcare.service.UserService;
import com.kasztelanic.carcare.service.dto.CostReportRequest;
import com.kasztelanic.carcare.service.dto.CostResultDto;
import com.kasztelanic.carcare.service.dto.VehicleDto;
import com.kasztelanic.carcare.service.mapper.VehicleMapper;

@RestController
@RequestMapping("/api/reports")
public class ReportsResource {

    private static final String EXTENSION = ".xlsx";

    @Autowired
    private ReportsService reportsService;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private VehicleMapper vehicleMapper;

    @Autowired
    private CostCalculator costCalculator;

    @Autowired
    private UserService userService;

    @Transactional
    @GetMapping("/vehicle/{id}")
    public ResponseEntity<byte[]> reportForVehicle(@PathVariable long id) throws IOException {
        Optional<VehicleDto> vehicleOptional = vehicleRepository.findById(id).map(vehicleMapper::vehicleToVehicleDto);
        if (vehicleOptional.isPresent()) {
            VehicleDto vehicle = vehicleOptional.get();
            User user = userService.getUserWithAuthorities().orElseThrow(IllegalStateException::new);
            Locale locale = Locale.forLanguageTag(user.getLangKey());
            byte[] bytes = reportsService.generateVehicleReport(vehicleOptional.get(), locale);
            String filename = vehicle.getLicensePlate().replaceAll("\\s+", "_") + EXTENSION;
            return prepareResponse(bytes, filename);
        }
        return ResponseEntity.notFound().build();
    }

    @Transactional
    @PostMapping("/costs")
    public ResponseEntity<byte[]> costReport(@RequestBody CostReportRequest costReportRequest) throws IOException {
        System.out.println(costReportRequest);
        User user = userService.getUserWithAuthorities().orElseThrow(IllegalStateException::new);
        Locale locale = Locale.forLanguageTag(user.getLangKey());
        List<CostResultDto> costs = vehicleRepository.findAllById(costReportRequest.getVehicleIds()).stream()
                .map(vehicleMapper::vehicleToVehicleDto)
                .map(v -> costCalculator.calculate(v, costReportRequest.getDateFrom(), costReportRequest.getDateTo()))
                .collect(Collectors.toList());
        byte[] bytes = reportsService.generateCostReport(costs, locale);
        String filename = "costs" + EXTENSION;
        return prepareResponse(bytes, filename);
    }

    private ResponseEntity<byte[]> prepareResponse(byte[] bytes, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.ms-excel"));
        headers.setContentDispositionFormData(filename, filename);
        headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
        return ResponseEntity.ok().headers(headers).body(bytes);
    }
}
