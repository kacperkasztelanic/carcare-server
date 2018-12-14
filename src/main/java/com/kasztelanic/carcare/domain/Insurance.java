package com.kasztelanic.carcare.domain;

import java.time.LocalDate;

import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

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
@EqualsAndHashCode(of = { "uuid" })
@ToString(of = { "validFrom" })
public class Insurance implements Remindable {

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
    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Getter
    @Setter
    @NotNull
    @Column(name = "valid_thru", nullable = false)
    private LocalDate validThru;

    @Getter
    @Setter
    @NotNull
    @Min(0)
    @Column(name = "cost_in_cents", nullable = false)
    private Integer costInCents;

    @Getter
    @Setter
    @NotNull
    @Column(nullable = false)
    private String details;

    @Getter
    @Setter
    @NotNull
    @JsonIgnore
    @JsonIgnoreProperties("")
    @ManyToOne(optional = false)
    @JoinColumn
    private Vehicle vehicle;

    @PersistenceConstructor
    @SuppressWarnings("all")
    private Insurance() {
        this.id = null;
        this.uuid = null;
    }

    @Builder
    private Insurance(VehicleEvent vehicleEvent, LocalDate validFrom, LocalDate validThru, Integer costInCents,
            String details, Vehicle vehicle) {
        this.vehicleEvent = vehicleEvent;
        this.validFrom = validFrom;
        this.validThru = validThru;
        this.costInCents = costInCents;
        this.details = details;
        this.vehicle = vehicle;
        this.id = null;
        this.uuid = UuidProvider.newUuid();
    }
}
