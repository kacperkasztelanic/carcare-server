package com.kasztelanic.carcare.service;

import com.kasztelanic.carcare.service.dto.AdminVehicleDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface AdminVehicleService {

    Page<AdminVehicleDto> findArchived(Pageable pageable);

    Optional<AdminVehicleDto> restoreVehicle(Long id);

    /**
     * Hard-purge an archived vehicle: its row, all five event tables' rows (FK-safe order), the
     * image file (after commit), and an in-transaction {@code VEHICLE_PURGED} audit event. Rejects
     * an unknown id ({@link java.util.NoSuchElementException} -> 404) and a non-archived vehicle
     * ({@link com.kasztelanic.carcare.service.exception.VehicleNotArchivedException} -> 409).
     */
    void purgeVehicle(Long id);
}
