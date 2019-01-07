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
@Table(name = "inspections")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@Accessors(chain = true)
@EqualsAndHashCode(of = { "id" })
@ToString(of = { "validThru", "vehicleEvent", "vehicle" })
public class Inspection implements Serializable {

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
    @NotNull
    @Length(min = 1, max = 30)
    @Column(name = "station", nullable = false, length = 30)
    private String station;

    @Getter
    @Setter
    @NotNull
    @Column(name = "valid_thru", nullable = false)
    private LocalDate validThru;

    @Getter
    @Setter
    @NotNull
    @Length(min = 1)
    @Column(name = "details", nullable = false, length = 65535, columnDefinition = "Text")
    private String details;

    @PersistenceConstructor
    @SuppressWarnings("all")
    private Inspection() {
        this.id = null;
    }

    @Builder
    private Inspection(VehicleEvent vehicleEvent, Integer costInCents, String station, LocalDate validThru,
            String details, Vehicle vehicle) {
        this.vehicleEvent = vehicleEvent;
        this.costInCents = costInCents;
        this.station = station;
        this.validThru = validThru;
        this.details = details;
        this.vehicle = vehicle;
        this.id = null;
    }
}
