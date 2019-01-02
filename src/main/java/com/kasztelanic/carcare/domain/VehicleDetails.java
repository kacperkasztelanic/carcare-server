package com.kasztelanic.carcare.domain;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Lob;
import javax.validation.constraints.Min;

import org.hibernate.validator.constraints.Length;
import org.springframework.data.annotation.PersistenceConstructor;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Embeddable
@ToString
public class VehicleDetails implements Serializable {

    private static final long serialVersionUID = 1L;

    @Getter
    @Setter
    @Column(name = "model_suffix", length = 30)
    private String modelSuffix;

    @Getter
    @Setter
    @Length(max = 17)
    @Column(name = "vin_number", length = 17)
    private String vinNumber;

    @Getter
    @Setter
    @Length(max = 10)
    @Column(name = "vehicle_card", length = 10)
    private String vehicleCard;

    @Getter
    @Setter
    @Length(max = 14)
    @Column(name = "registration_certificate", length = 14)
    private String registrationCertificate;

    @Getter
    @Setter
    @Column(name = "year_of_manufacture")
    private Integer yearOfManufacture;

    @Getter
    @Setter
    @Min(0)
    @Column(name = "engine_volume_in_cm3")
    private Integer engineVolume;

    @Getter
    @Setter
    @Min(0)
    @Column(name = "engine_power_in_kw")
    private Integer enginePower;

    @Getter
    @Setter
    @Min(0)
    @Column(name = "weight_in_kg")
    private Integer weight;

    @Getter
    @Setter
    @Column(name = "notes", length = 65535, columnDefinition = "Text")
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
    private VehicleDetails(String modelSuffix, String vinNumber, String vehicleCard, String registrationCertificate,
            Integer yearOfManufacture, Integer engineVolume, Integer enginePower, Integer weight, String notes,
            byte[] image, String imageContentType) {
        this.modelSuffix = modelSuffix;
        this.vinNumber = vinNumber;
        this.vehicleCard = vehicleCard;
        this.registrationCertificate = registrationCertificate;
        this.yearOfManufacture = yearOfManufacture;
        this.engineVolume = engineVolume;
        this.enginePower = enginePower;
        this.weight = weight;
        this.notes = notes;
        this.image = image;
        this.imageContentType = imageContentType;
    }
}
