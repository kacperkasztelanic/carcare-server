package com.kasztelanic.carcare.repository;

import com.kasztelanic.carcare.domain.ReminderAdvance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReminderAdvanceRepository extends JpaRepository<ReminderAdvance, Long> {

    Optional<ReminderAdvance> findByDays(Integer days);
}
