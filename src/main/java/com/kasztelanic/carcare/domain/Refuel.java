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

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

@Entity
@Table(name = "refuels")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@Accessors(chain = true)
@EqualsAndHashCode(of = { "id" })
@ToString(of = { "costInCents", "volume" }, includeFieldNames = false)
public class Refuel implements Serializable {

    private static final long serialVersionUID = 1L;

    @Getter
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private final Long id;

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
    @Min(0)
    @Column(name = "volume_in_cm3")
    private Integer volume;

    @Getter
    @Setter
    @NotNull
    @Length(min = 1, max = 30)
    @Column(name = "station", nullable = false, length = 30)
    private String station;

    @PersistenceConstructor
    @SuppressWarnings("all")
    private Refuel() {
        this.id = null;
    }

    @Builder
    private Refuel(VehicleEvent vehicleEvent, Vehicle vehicle, Integer costInCents, Integer volume, String station) {
        this.vehicleEvent = vehicleEvent;
        this.vehicle = vehicle;
        this.costInCents = costInCents;
        this.volume = volume;
        this.station = station;
        this.id = null;
    }
}
