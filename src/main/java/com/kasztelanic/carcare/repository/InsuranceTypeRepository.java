package com.kasztelanic.carcare.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.kasztelanic.carcare.domain.InsuranceType;

@Repository
public interface InsuranceTypeRepository extends JpaRepository<InsuranceType, Long> {

    Optional<InsuranceType> findByType(String type);
}
