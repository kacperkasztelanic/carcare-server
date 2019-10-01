package com.kasztelanic.carcare.web.rest;

import com.kasztelanic.carcare.domain.User;
import com.kasztelanic.carcare.service.ReportService;
import com.kasztelanic.carcare.service.UserService;
import com.kasztelanic.carcare.service.dto.CostRequest;
import com.kasztelanic.carcare.service.dto.Report;
import com.kasztelanic.carcare.service.exception.ReportGenerationException;
import com.kasztelanic.carcare.util.EitherUtil;

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

import io.vavr.control.Either;

@RestController
@RequestMapping("/api/reports")
public class ReportResource {

    private final ReportService reportService;
    private final UserService userService;

    @Autowired
    public ReportResource(ReportService reportService, UserService userService) {
        this.reportService = reportService;
        this.userService = userService;
    }

    @GetMapping("/vehicle/{id}")
    public ResponseEntity<byte[]> reportForVehicle(@PathVariable long id) {
        User user = userService.getUserWithAuthoritiesOrFail();
        Either<ReportGenerationException, ResponseEntity<byte[]>> either = reportService
                .generateVehicleReport(id, user)//
                .map(e -> e.map(ReportResource::prepareResponse))//
                .orElseGet(() -> Either.right(ResponseEntity.notFound().build()));
        return EitherUtil.getOrThrow(either);
    }

    @PostMapping("/costs")
    public ResponseEntity<byte[]> costReport(@RequestBody CostRequest costRequest) {
        User user = userService.getUserWithAuthoritiesOrFail();
        Either<ReportGenerationException, ResponseEntity<byte[]>> either = reportService
                .generateCostReport(costRequest, user)//
                .map(ReportResource::prepareResponse);
        return EitherUtil.getOrThrow(either);

    }

    private static ResponseEntity<byte[]> prepareResponse(Report report) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.ms-excel"));
        headers.setContentDispositionFormData(report.getName(), report.getName());
        headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
        return ResponseEntity.ok().headers(headers).body(report.getBytes());
    }
}
