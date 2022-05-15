package com.kasztelanic.carcare.repository;

import com.kasztelanic.carcare.domain.FuelType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FuelTypeRepository extends JpaRepository<FuelType, Long> {

    Optional<FuelType> findByType(String type);
}
