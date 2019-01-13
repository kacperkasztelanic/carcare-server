package com.kasztelanic.carcare.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.kasztelanic.carcare.domain.FuelType;

@Repository
public interface FuelTypeRepository extends JpaRepository<FuelType, Long> {

    Optional<FuelType> findByType(String type);
}
