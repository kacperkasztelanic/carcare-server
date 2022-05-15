package com.kasztelanic.carcare.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.validator.constraints.Length;
import org.springframework.data.annotation.PersistenceConstructor;

import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

@Entity
@Table(name = "vehicles")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@EqualsAndHashCode(of = {"id"})
@ToString(of = {"make", "model", "licensePlate"}, includeFieldNames = false)
public class Vehicle implements Serializable {

    private static final long serialVersionUID = 1L;

    @Getter
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private final Long id;

    @Getter
    @Setter
    @NotNull
    @Length(min = 1, max = 20)
    @Column(name = "make", nullable = false, length = 20)
    private String make;

    @Getter
    @Setter
    @NotNull
    @Length(min = 1, max = 20)
    @Column(name = "model", nullable = false, length = 20)
    private String model;

    @Getter
    @Setter
    @NotNull
    @Length(min = 1, max = 10)
    @Column(name = "license_plate", nullable = false, length = 10)
    private String licensePlate;

    @Getter
    @Setter
    @NotNull
    @ManyToOne(optional = false)
    private FuelType fuelType;

    @Getter
    @Setter
    @NotNull
    @Embedded
    private VehicleDetails vehicleDetails;

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
    }

    @Builder
    @SuppressWarnings("all")
    private Vehicle(String make, String model, String licensePlate, FuelType fuelType, VehicleDetails vehicleDetails,
                    User owner) {
        this.make = make;
        this.model = model;
        this.licensePlate = licensePlate;
        this.fuelType = fuelType;
        this.vehicleDetails = vehicleDetails;
        this.owner = owner;
        this.id = null;
    }
}
