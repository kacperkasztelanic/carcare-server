package com.kasztelanic.carcare.service.impl;

import com.kasztelanic.carcare.repository.VehicleRepository;
import com.kasztelanic.carcare.service.AdminVehicleService;
import com.kasztelanic.carcare.service.dto.AdminVehicleDto;
import com.kasztelanic.carcare.service.mapper.AdminVehicleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class AdminVehicleServiceImpl implements AdminVehicleService {

    /**
     * Applied when the caller supplies no sort. Kept here rather than in the query so an explicit
     * client sort is honoured; {@code id} is the tiebreaker that makes any ordering deterministic.
     */
    private static final Sort ARCHIVED_SORT = Sort.by(Sort.Order.desc("archivedAt"), Sort.Order.asc("id"));

    private final VehicleRepository vehicleRepository;
    private final AdminVehicleMapper adminVehicleMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<AdminVehicleDto> findArchived(Pageable pageable) {
        return vehicleRepository.findAllArchived(withDeterministicSort(pageable))
            .map(adminVehicleMapper::vehicleToAdminVehicleDto);
    }

    private static Pageable withDeterministicSort(Pageable pageable) {
        if (pageable.getSort().isSorted()) {
            // Append id so a client sort on a non-unique column still yields a stable page boundary.
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                pageable.getSort().and(Sort.by(Sort.Order.asc("id"))));
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), ARCHIVED_SORT);
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
