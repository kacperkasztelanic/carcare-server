package com.kasztelanic.carcare.domain;

import java.io.Serializable;
import java.time.LocalDate;

import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.validator.constraints.Length;
import org.springframework.data.annotation.PersistenceConstructor;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.kasztelanic.carcare.util.UuidProvider;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "routine_services")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@EqualsAndHashCode(of = { "uuid" })
@ToString(of = { "uuid" }, includeFieldNames = false)
public class RoutineService implements Serializable {

    private static final long serialVersionUID = 1L;
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private final Long id;

    @Getter
    @NotNull
    @Column(nullable = false, unique = true, updatable = false, length = 36)
    private final String uuid;

    @Getter
    @Setter
    @NotNull
    @Embedded
    private VehicleEvent vehicleEvent;

    @Getter
    @Setter
    @NotNull
    @JsonIgnore
    @JsonIgnoreProperties("")
    @ManyToOne(optional = false)
    @JoinColumn
    private Vehicle vehicle;

    @Getter
    @Setter
    @Column(name = "next_by_mileage")
    private Integer nextByMileage;

    @Getter
    @Setter
    @Column(name = "next_by_date")
    private LocalDate nextByDate;

    @Getter
    @Setter
    @NotNull
    @Length(min = 1, max = 30)
    @Column(name = "station", nullable = false, length = 30)
    private String station;

    @Getter
    @Setter
    @NotNull
    @Length(min = 1)
    @Column(name = "details", nullable = false)
    private String details;

    @PersistenceConstructor
    @SuppressWarnings("all")
    private RoutineService() {
        this.id = null;
        this.uuid = null;
    }

    @Builder
    private RoutineService(VehicleEvent vehicleEvent, Vehicle vehicle, Integer nextByMileage, LocalDate nextByDate,
            String station, String details) {
        this.vehicleEvent = vehicleEvent;
        this.vehicle = vehicle;
        this.nextByMileage = nextByMileage;
        this.nextByDate = nextByDate;
        this.station = station;
        this.details = details;
        this.id = null;
        this.uuid = UuidProvider.newUuid();
    }
}
