package com.kasztelanic.carcare.domain;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.Min;
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
@Table(name = "repairs")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@EqualsAndHashCode(of = { "uuid" })
@ToString(of = { "uuid" })
public class Repair implements Serializable {

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
    @NotNull
    @Min(0)
    @Column(name = "cost_in_cents", nullable = false)
    private Integer costInCents;

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
    private Repair() {
        this.id = null;
        this.uuid = null;
    }

    @Builder
    private Repair(VehicleEvent vehicleEvent, Integer costInCents, String station, String details, Vehicle vehicle) {
        this.vehicleEvent = vehicleEvent;
        this.costInCents = costInCents;
        this.station = station;
        this.details = details;
        this.vehicle = vehicle;
        this.id = null;
        this.uuid = UuidProvider.newUuid();
    }
}
