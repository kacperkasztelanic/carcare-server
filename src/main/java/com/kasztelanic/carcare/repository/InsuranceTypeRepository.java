package com.kasztelanic.carcare.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kasztelanic.carcare.domain.InsuranceType;

public interface InsuranceTypeRepository extends JpaRepository<InsuranceType, Long> {

    Optional<InsuranceType> findByType(String type);
}
