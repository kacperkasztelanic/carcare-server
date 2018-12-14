package com.kasztelanic.carcare.domain;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Lob;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;

import org.springframework.data.annotation.PersistenceConstructor;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Embeddable
@EqualsAndHashCode(of = { "vinNumber" })
@ToString
public class VehicleDetails implements Serializable {

    private static final long serialVersionUID = 1L;

    @Getter
    @Setter
    @Column(name = "vin_number")
    private String vinNumber;

    @Getter
    @Setter
    @Column(name = "year_of_manufacture")
    private Integer yearOfManufacture;

    @Getter
    @Setter
    @DecimalMin(value = "0")
    @Column(name = "engine_volume")
    private Double engineVolume;

    @Getter
    @Setter
    @Min(value = 0)
    @Column(name = "engine_power")
    private Integer enginePower;

    @Getter
    @Setter
    @DecimalMin(value = "0")
    @Column(name = "weight")
    private Double weight;

    @Getter
    @Setter
    @Lob
    @Column(name = "notes")
    private String notes;

    @Getter
    @Setter
    @Lob
    @Column(name = "image")
    private byte[] image;

    @Getter
    @Setter
    @Column(name = "image_content_type")
    private String imageContentType;

    @PersistenceConstructor
    private VehicleDetails() {
    }

    @Builder
    @SuppressWarnings("all")
    private VehicleDetails(String vinNumber, Integer yearOfManufacture, Double engineVolume, Integer enginePower,
            Double weight, String notes, byte[] image, String imageContentType) {
        this.vinNumber = vinNumber;
        this.yearOfManufacture = yearOfManufacture;
        this.engineVolume = engineVolume;
        this.enginePower = enginePower;
        this.weight = weight;
        this.notes = notes;
        this.image = image;
        this.imageContentType = imageContentType;
    }
}
