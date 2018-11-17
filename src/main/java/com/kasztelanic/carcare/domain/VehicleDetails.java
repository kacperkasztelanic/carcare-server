package com.kasztelanic.carcare.domain;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import javax.validation.constraints.*;

import java.io.Serializable;
import java.util.Objects;

/**
 * A VehicleDetails.
 */
@Entity
@Table(name = "vehicle_details")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class VehicleDetails implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vin_number")
    private String vinNumber;

    @Column(name = "year_of_manufacture")
    private Integer yearOfManufacture;

    @DecimalMin(value = "0")
    @Column(name = "engine_volume")
    private Double engineVolume;

    @Min(value = 0)
    @Column(name = "engine_power")
    private Integer enginePower;

    @DecimalMin(value = "0")
    @Column(name = "weight")
    private Double weight;

    @Lob
    @Column(name = "notes")
    private String notes;

    @Lob
    @Column(name = "image")
    private byte[] image;

    @Column(name = "image_content_type")
    private String imageContentType;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getVinNumber() {
        return vinNumber;
    }

    public VehicleDetails vinNumber(String vinNumber) {
        this.vinNumber = vinNumber;
        return this;
    }

    public void setVinNumber(String vinNumber) {
        this.vinNumber = vinNumber;
    }

    public Integer getYearOfManufacture() {
        return yearOfManufacture;
    }

    public VehicleDetails yearOfManufacture(Integer yearOfManufacture) {
        this.yearOfManufacture = yearOfManufacture;
        return this;
    }

    public void setYearOfManufacture(Integer yearOfManufacture) {
        this.yearOfManufacture = yearOfManufacture;
    }

    public Double getEngineVolume() {
        return engineVolume;
    }

    public VehicleDetails engineVolume(Double engineVolume) {
        this.engineVolume = engineVolume;
        return this;
    }

    public void setEngineVolume(Double engineVolume) {
        this.engineVolume = engineVolume;
    }

    public Integer getEnginePower() {
        return enginePower;
    }

    public VehicleDetails enginePower(Integer enginePower) {
        this.enginePower = enginePower;
        return this;
    }

    public void setEnginePower(Integer enginePower) {
        this.enginePower = enginePower;
    }

    public Double getWeight() {
        return weight;
    }

    public VehicleDetails weight(Double weight) {
        this.weight = weight;
        return this;
    }

    public void setWeight(Double weight) {
        this.weight = weight;
    }

    public String getNotes() {
        return notes;
    }

    public VehicleDetails notes(String notes) {
        this.notes = notes;
        return this;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public byte[] getImage() {
        return image;
    }

    public VehicleDetails image(byte[] image) {
        this.image = image;
        return this;
    }

    public void setImage(byte[] image) {
        this.image = image;
    }

    public String getImageContentType() {
        return imageContentType;
    }

    public VehicleDetails imageContentType(String imageContentType) {
        this.imageContentType = imageContentType;
        return this;
    }

    public void setImageContentType(String imageContentType) {
        this.imageContentType = imageContentType;
    }
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        VehicleDetails vehicleDetails = (VehicleDetails) o;
        if (vehicleDetails.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), vehicleDetails.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "VehicleDetails{" +
            "id=" + getId() +
            ", vinNumber='" + getVinNumber() + "'" +
            ", yearOfManufacture=" + getYearOfManufacture() +
            ", engineVolume=" + getEngineVolume() +
            ", enginePower=" + getEnginePower() +
            ", weight=" + getWeight() +
            ", notes='" + getNotes() + "'" +
            ", image='" + getImage() + "'" +
            ", imageContentType='" + getImageContentType() + "'" +
            "}";
    }
}
