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
import com.kasztelanic.carcare.service.dto.CostRequest;
import com.kasztelanic.carcare.service.dto.CostResult;
import com.kasztelanic.carcare.service.dto.PeriodVehicle;
import com.kasztelanic.carcare.service.dto.VehicleRichDto;
import com.kasztelanic.carcare.service.mapper.VehicleRichMapper;

@RestController
@RequestMapping("/api/reports")
public class ReportsResource {

    private static final String EXTENSION = ".xlsx";

    @Autowired
    private ReportsService reportsService;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private VehicleRichMapper vehicleMapper;

    @Autowired
    private CostCalculator costCalculator;

    @Autowired
    private UserService userService;

    @Transactional
    @GetMapping("/vehicle/{id}")
    public ResponseEntity<byte[]> reportForVehicle(@PathVariable long id) throws IOException {
        Optional<VehicleRichDto> vehicleOptional = vehicleRepository.findByIdAndOwnerIsCurrentUser(id)
                .map(vehicleMapper::vehicleToVehicleDto);
        if (vehicleOptional.isPresent()) {
            VehicleRichDto vehicle = vehicleOptional.get();
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
    public ResponseEntity<byte[]> costReport(@RequestBody CostRequest costRequest) throws IOException {
        User user = userService.getUserWithAuthorities().orElseThrow(IllegalStateException::new);
        Locale locale = Locale.forLanguageTag(user.getLangKey());
        List<VehicleRichDto> vehicles = vehicleRepository.findAllByIdAndOwnerIsCurrentUser(costRequest.getVehicleIds())
                .stream().map(vehicleMapper::vehicleToVehicleDto).collect(Collectors.toList());
        List<CostResult> costs = vehicles.stream()
                .map(v -> costCalculator
                        .calculate(PeriodVehicle.of(v.getId(), costRequest.getDateFrom(), costRequest.getDateTo()), v))
                .collect(Collectors.toList());
        byte[] bytes = reportsService.generateCostReport(costs, vehicles, locale);
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
