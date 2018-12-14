package com.kasztelanic.carcare.domain;

import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
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
@Table(name = "vehicle")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@EqualsAndHashCode(of = { "id" })
@ToString(of = { "id", "make", "model", "licensePlate" }, includeFieldNames = false)
public class Vehicle {

    @Getter
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
    @Column(name = "make", nullable = false)
    private String make;

    @Getter
    @Setter
    @NotNull
    @Column(name = "model", nullable = false)
    private String model;

    @Getter
    @Setter
    @NotNull
    @Column(name = "license_plate", nullable = false)
    private String licensePlate;

    @Getter
    @Setter
    @NotNull
    @Embedded
    private VehicleDetails vehicleDetails;

    @Getter
    @Setter
    @NotNull
    @OneToMany(mappedBy = "vehicle", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Insurance> insurance;

    @Getter
    @Setter
    @JsonIgnore
    @JsonIgnoreProperties("")
    @NotNull
    @ManyToOne(optional = false)
    private User owner;

    @PersistenceConstructor
    @SuppressWarnings("all")
    private Vehicle() {
        this.id = null;
        this.uuid = null;
    }

    @Builder
    private Vehicle(String make, String model, String licensePlate, VehicleDetails vehicleDetails, User owner,
            Set<Insurance> insurance) {
        this.make = make;
        this.model = model;
        this.licensePlate = licensePlate;
        this.vehicleDetails = vehicleDetails;
        this.owner = owner;
        this.insurance = insurance;
        this.id = null;
        this.uuid = UuidProvider.newUuid();
    }

    public void addInsurance(Insurance insurance) {
        insurance.setVehicle(this);
        this.insurance.add(insurance);
    }
}