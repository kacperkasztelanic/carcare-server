package com.kasztelanic.carcare.repository;

import com.kasztelanic.carcare.domain.InsuranceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InsuranceTypeRepository extends JpaRepository<InsuranceType, Long> {

    Optional<InsuranceType> findByType(String type);
}
