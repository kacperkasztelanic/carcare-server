package com.kasztelanic.carcare.service.impl;

import com.kasztelanic.carcare.domain.Inspection;
import com.kasztelanic.carcare.domain.Insurance;
import com.kasztelanic.carcare.domain.PersistentAuditEvent;
import com.kasztelanic.carcare.domain.Refuel;
import com.kasztelanic.carcare.domain.Repair;
import com.kasztelanic.carcare.domain.RoutineService;
import com.kasztelanic.carcare.domain.Vehicle;
import com.kasztelanic.carcare.repository.InspectionRepository;
import com.kasztelanic.carcare.repository.InsuranceRepository;
import com.kasztelanic.carcare.repository.PersistenceAuditEventRepository;
import com.kasztelanic.carcare.repository.RefuelRepository;
import com.kasztelanic.carcare.repository.RepairRepository;
import com.kasztelanic.carcare.repository.RoutineServiceRepository;
import com.kasztelanic.carcare.repository.VehicleRepository;
import com.kasztelanic.carcare.security.SecurityUtils;
import com.kasztelanic.carcare.service.AdminVehicleService;
import com.kasztelanic.carcare.service.ImageStorageService;
import com.kasztelanic.carcare.service.dto.AdminVehicleDto;
import com.kasztelanic.carcare.service.exception.VehicleNotArchivedException;
import com.kasztelanic.carcare.service.mapper.AdminVehicleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AdminVehicleServiceImpl implements AdminVehicleService {

    static final String PURGE_AUDIT_EVENT_TYPE = "VEHICLE_PURGED";

    /**
     * Applied when the caller supplies no sort. Kept here rather than in the query so an explicit
     * client sort is honoured; {@code id} is the tiebreaker that makes any ordering deterministic.
     */
    private static final Sort ARCHIVED_SORT = Sort.by(Sort.Order.desc("archivedAt"), Sort.Order.asc("id"));

    private final VehicleRepository vehicleRepository;
    private final AdminVehicleMapper adminVehicleMapper;
    private final RefuelRepository refuelRepository;
    private final RepairRepository repairRepository;
    private final RoutineServiceRepository routineServiceRepository;
    private final InspectionRepository inspectionRepository;
    private final InsuranceRepository insuranceRepository;
    private final ImageStorageService imageStorageService;
    private final PersistenceAuditEventRepository persistenceAuditEventRepository;
    private final Clock clock;

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

    @Override
    public void purgeVehicle(Long id) {
        Vehicle vehicle = vehicleRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Vehicle " + id + " does not exist"));
        // Known residual risk (impl-review F2, accepted 2026-08-29): this archivedAt check is
        // check-then-act with no @Version on Vehicle, so a concurrent restoreVehicle could commit
        // between this read and the purge commit. Accepted because the deployment is single-admin
        // (P7); revisit if concurrent admin sessions are ever supported.
        if (vehicle.getArchivedAt() == null) {
            throw new VehicleNotArchivedException(id);
        }

        // Read the image filename before any row delete; the file is removed only after commit.
        String image = vehicle.getVehicleDetails() == null ? null : vehicle.getVehicleDetails().getImage();

        List<Refuel> refuels = refuelRepository.findByVehicleId(id);
        List<Repair> repairs = repairRepository.findByVehicleId(id);
        List<RoutineService> routineServices = routineServiceRepository.findByVehicleId(id);
        List<Inspection> inspections = inspectionRepository.findByVehicleId(id);
        List<Insurance> insurances = insuranceRepository.findByVehicleId(id);

        Map<String, String> auditData = new LinkedHashMap<>();
        auditData.put("vehicleId", String.valueOf(id));
        auditData.put("ownerLogin", vehicle.getOwner().getLogin());
        auditData.put("refuels", String.valueOf(refuels.size()));
        auditData.put("repairs", String.valueOf(repairs.size()));
        auditData.put("routineServices", String.valueOf(routineServices.size()));
        auditData.put("inspections", String.valueOf(inspections.size()));
        auditData.put("insurances", String.valueOf(insurances.size()));
        auditData.put("image", image == null ? "" : image);

        // File delete is unrecoverable on rollback, so defer it to a committed transaction only.
        // The purge is durable by the time afterCompletion runs, so the callback must never throw
        // (that would surface as a misleading 500 for a committed delete) and must not silently
        // swallow a failed file delete.
        if (image != null && !image.isEmpty()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_COMMITTED) {
                        try {
                            if (!imageStorageService.delete(image)) {
                                log.warn("Purge committed but image file was not deleted: {}", image);
                            }
                        } catch (RuntimeException e) {
                            log.warn("Purge committed but image file deletion failed: {}", image, e);
                        }
                    }
                }
            });
        }

        // Entity-level deletes only (no bulk delete queries) so Hibernate's EntityDeleteAction keeps the
        // ehcache L2 regions coherent in dev/prod. Events before the vehicle: FKs are per-statement.
        refuelRepository.deleteAll(refuels);
        repairRepository.deleteAll(repairs);
        routineServiceRepository.deleteAll(routineServices);
        inspectionRepository.deleteAll(inspections);
        insuranceRepository.deleteAll(insurances);
        vehicleRepository.delete(vehicle);

        PersistentAuditEvent auditEvent = new PersistentAuditEvent();
        auditEvent.setPrincipal(SecurityUtils.getCurrentUserLogin().orElse("unknown"));
        auditEvent.setAuditEventType(PURGE_AUDIT_EVENT_TYPE);
        auditEvent.setAuditEventDate(clock.instant());
        auditEvent.setData(auditData);
        persistenceAuditEventRepository.save(auditEvent);
    }
}
