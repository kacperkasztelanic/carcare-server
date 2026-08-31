package com.kasztelanic.carcare.service.impl;

import com.kasztelanic.carcare.domain.User;
import com.kasztelanic.carcare.domain.Vehicle;
import com.kasztelanic.carcare.repository.VehicleRepository;
import com.kasztelanic.carcare.service.ImageStorageService;
import com.kasztelanic.carcare.service.VehicleScopeService;
import com.kasztelanic.carcare.service.VehicleService;
import com.kasztelanic.carcare.service.dto.VehicleDto;
import com.kasztelanic.carcare.service.mapper.VehicleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleServiceImpl implements VehicleService {

    private final VehicleRepository vehicleRepository;
    private final VehicleMapper vehicleMapper;
    private final ImageStorageService imageStorageService;
    private final VehicleScopeService vehicleScopeService;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public Optional<VehicleDto> getVehicle(Long id) {
        return vehicleScopeService.findActiveOwnedVehicle(id)//
            .map(vehicleMapper::vehicleToVehicleDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VehicleDto> getAllVehicles() {
        return vehicleRepository.findByOwnerIsCurrentUser().stream()//
            .map(vehicleMapper::vehicleToVehicleDto)//
            .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public VehicleDto addVehicle(VehicleDto vehicleDto, User user) {
        Vehicle vehicle = vehicleMapper.vehicleDtoToVehicle(vehicleDto);
        vehicle.setOwner(user);
        return vehicleMapper.vehicleToVehicleDto(vehicleRepository.save(vehicle));
    }

    @Override
    @Transactional
    public Optional<VehicleDto> editVehicle(Long id, VehicleDto vehicleDto) {
        return vehicleScopeService.findActiveOwnedVehicle(id)//
            .map(i -> updateVehicle(i, vehicleMapper.vehicleDtoToVehicle(vehicleDto)))//
            .map(vehicleRepository::save)//
            .map(vehicleMapper::vehicleToVehicleDto);
    }

    @Override
    @Transactional
    public Optional<VehicleDto> deleteVehicle(Long id) {
        return vehicleScopeService.findActiveOwnedVehicle(id)//
            .map(vehicle -> {
                VehicleDto vehicleDto = vehicleMapper.vehicleToVehicleDto(vehicle);
                vehicle.setArchivedAt(Instant.now(clock));
                vehicleRepository.save(vehicle);
                return vehicleDto;
            });
    }

    private Vehicle updateVehicle(Vehicle vehicle, Vehicle updatedVehicle) {
        // Both filenames must be read before the entity is mutated below: the mapper has already
        // written the replacement file to disk, so getImage() is about to be overwritten.
        String replacedImage = vehicle.getVehicleDetails().getImage();
        String replacementImage = updatedVehicle.getVehicleDetails().getImage();
        deferImageCleanup(replacedImage, replacementImage);
        vehicle.setFuelType(updatedVehicle.getFuelType());
        vehicle.setLicensePlate(updatedVehicle.getLicensePlate());
        vehicle.setMake(updatedVehicle.getMake());
        vehicle.setModel(updatedVehicle.getModel());
        vehicle.setVehicleDetails(updatedVehicle.getVehicleDetails());
        return vehicle;
    }

    /**
     * Deletes the replaced image file only once the edit transaction commits — a rollback must not
     * destroy it (FR-004). If the transaction rolls back, the replacement file the mapper has
     * already written is the orphan to remove instead. Mirrors
     * {@code AdminVehicleServiceImpl}'s post-commit delete discipline: the callback never throws
     * and never silently swallows a failed delete. With no active synchronization (no such caller
     * exists today) it degrades to a no-op rather than throwing.
     */
    private void deferImageCleanup(String replacedImage, String replacementImage) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                String orphan = status == STATUS_COMMITTED ? replacedImage : replacementImage;
                if (orphan == null || orphan.isEmpty()) {
                    return;
                }
                try {
                    if (!imageStorageService.delete(orphan)) {
                        log.warn("Image file was not deleted after transaction completion: {}", orphan);
                    }
                } catch (RuntimeException e) {
                    log.warn("Image file deletion failed after transaction completion: {}", orphan, e);
                }
            }
        });
    }
}
