package com.kasztelanic.carcare.domain;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;
import org.springframework.data.annotation.PersistenceConstructor;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Entity
@Table(name = "vehicle")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@EqualsAndHashCode(of = { "id" })
@ToString(of = { "id", "make", "model", "licensePlate" }, includeFieldNames = false)
public class Vehicle implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private final Long id;

    @NotNull
    @Column(name = "make", nullable = false)
    @Getter
    private final String make;

    @NotNull
    @Column(name = "model", nullable = false)
    @Getter
    private final String model;

    @NotNull
    @Column(name = "license_plate", nullable = false)
    @Getter
    private final String licensePlate;

    @OneToOne
    @JoinColumn(unique = true)
    @Getter
    @Cascade(CascadeType.ALL)
    private final VehicleDetails vehicleDetails;

    @ManyToOne(optional = false)
    @NotNull
    @JsonIgnoreProperties("")
    @Getter
    @JsonIgnore
    private final User owner;

    @PersistenceConstructor
    @SuppressWarnings("all")
    private Vehicle() {
        this.make = null;
        this.model = null;
        this.licensePlate = null;
        this.vehicleDetails = null;
        this.owner = null;
        this.id = null;
    }

    @Builder
    private Vehicle(@NotNull String make, @NotNull String model, @NotNull String licensePlate,
            VehicleDetails vehicleDetails, @NotNull User owner) {
        this.make = make;
        this.model = model;
        this.licensePlate = licensePlate;
        this.vehicleDetails = vehicleDetails;
        this.owner = owner;
        this.id = null;
    }
}