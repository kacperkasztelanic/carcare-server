package com.kasztelanic.carcare.domain;

import java.io.Serializable;
import java.time.LocalDate;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import org.springframework.data.annotation.PersistenceConstructor;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Embeddable
@ToString(includeFieldNames = false)
public class VehicleEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull
    @Min(0)
    @Getter
    @Column(nullable = false)
    private final Integer mileage;

    @NotNull
    @Getter
    @Column(nullable = false)
    private final LocalDate date;

    public static VehicleEvent of(Integer mileage, LocalDate date) {
        return new VehicleEvent(mileage, date);
    }

    @PersistenceConstructor
    @SuppressWarnings("all")
    private VehicleEvent() {
        this.mileage = null;
        this.date = null;
    }

    @Builder
    private VehicleEvent(Integer mileage, LocalDate date) {
        this.mileage = mileage;
        this.date = date;
    }
}
