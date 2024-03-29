package com.kasztelanic.carcare.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
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
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.LocalDate;

@Entity
@Table(name = "insurances")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@Accessors(chain = true)
@EqualsAndHashCode(of = {"id"})
@ToString(of = {"validThru", "vehicleEvent", "vehicle"})
public class Insurance implements Serializable {

    private static final long serialVersionUID = 1L;

    @Getter
    @Setter
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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
    @Length(max = 20)
    @Column(name = "number", length = 20)
    private String number;

    @Getter
    @Setter
    @Length(max = 20)
    @Column(name = "insurer", length = 20)
    private String insurer;

    @Getter
    @Setter
    @NotNull
    @Column(name = "details", nullable = false, length = 65535, columnDefinition = "clob")
    private String details;

    @Getter
    @Setter
    @NotNull
    @ManyToOne(optional = false)
    private InsuranceType insuranceType;

    @PersistenceConstructor
    @SuppressWarnings("all")
    private Insurance() {
        this.id = null;
    }

    @Builder
    @SuppressWarnings("all")
    private Insurance(VehicleEvent vehicleEvent, LocalDate validFrom, LocalDate validThru, Integer costInCents,
                      String number, String insurer, String details, InsuranceType insuranceType, Vehicle vehicle) {
        this.vehicleEvent = vehicleEvent;
        this.validFrom = validFrom;
        this.validThru = validThru;
        this.costInCents = costInCents;
        this.number = number;
        this.insurer = insurer;
        this.details = details;
        this.insuranceType = insuranceType;
        this.vehicle = vehicle;
        this.id = null;
    }
}
