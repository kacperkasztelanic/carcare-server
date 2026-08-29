package com.kasztelanic.carcare.service.impl;

import com.kasztelanic.carcare.repository.VehicleRepository;
import com.kasztelanic.carcare.service.AdminVehicleService;
import com.kasztelanic.carcare.service.dto.AdminVehicleDto;
import com.kasztelanic.carcare.service.mapper.AdminVehicleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class AdminVehicleServiceImpl implements AdminVehicleService {

    private final VehicleRepository vehicleRepository;
    private final AdminVehicleMapper adminVehicleMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<AdminVehicleDto> findArchived(Pageable pageable) {
        return vehicleRepository.findAllArchived(pageable).map(adminVehicleMapper::vehicleToAdminVehicleDto);
    }

    @Override
    public Optional<AdminVehicleDto> restoreVehicle(Long id) {
        return vehicleRepository.findById(id)
            .map(vehicle -> {
                vehicle.setArchivedAt(null);
                return adminVehicleMapper.vehicleToAdminVehicleDto(vehicleRepository.save(vehicle));
            });
    }
}
