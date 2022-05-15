package com.kasztelanic.carcare.domain;

import lombok.Getter;
import lombok.ToString;
import org.springframework.data.annotation.PersistenceConstructor;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.LocalDate;

@Embeddable
@ToString(includeFieldNames = false)
public class VehicleEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @Getter
    @NotNull
    @Min(0)
    @Column(name = "mileage", nullable = false)
    private final Integer mileage;

    @Getter
    @NotNull
    @Column(name = "date", nullable = false)
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

    private VehicleEvent(Integer mileage, LocalDate date) {
        this.mileage = mileage;
        this.date = date;
    }
}
