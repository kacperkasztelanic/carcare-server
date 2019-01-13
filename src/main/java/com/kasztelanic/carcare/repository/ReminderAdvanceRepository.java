package com.kasztelanic.carcare.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.kasztelanic.carcare.domain.ReminderAdvance;

@Repository
public interface ReminderAdvanceRepository extends JpaRepository<ReminderAdvance, Long> {

    Optional<ReminderAdvance> findByDays(Integer days);
}
