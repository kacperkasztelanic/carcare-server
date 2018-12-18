package com.kasztelanic.carcare.web.rest;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kasztelanic.carcare.reports.ReportsService;
import com.kasztelanic.carcare.repository.VehicleRepository;
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

    @GetMapping("/vehicle/{id}")
    public ResponseEntity<byte[]> reportForVehicle(@PathVariable long id) {
        Optional<VehicleDto> vehicleOptional = vehicleRepository.findById(id).map(vehicleMapper::vehicleToVehicleDto);
        if (vehicleOptional.isPresent()) {
            VehicleDto vehicle = vehicleOptional.get();
            byte[] bytes = reportsService.generateVehicleReport(vehicleOptional.get());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.ms-excel"));
            String filename = vehicle.getLicensePlate().replaceAll("\\s+", "_") + EXTENSION;
            headers.setContentDispositionFormData(filename, filename);
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
            return ResponseEntity.ok().headers(headers).body(bytes);
        }
        return ResponseEntity.notFound().build();
    }
}
