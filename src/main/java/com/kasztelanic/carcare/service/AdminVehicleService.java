package com.kasztelanic.carcare.service;

import com.kasztelanic.carcare.service.dto.AdminVehicleDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface AdminVehicleService {

    Page<AdminVehicleDto> findArchived(Pageable pageable);

    Optional<AdminVehicleDto> restoreVehicle(Long id);
}
