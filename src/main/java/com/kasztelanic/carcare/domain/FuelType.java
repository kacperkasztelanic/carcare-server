package com.kasztelanic.carcare.domain;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.validator.constraints.Length;
import org.springframework.data.annotation.PersistenceConstructor;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Entity
@Table(name = "fuel_types")
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
@EqualsAndHashCode(of = { "type" })
@ToString(of = { "type" }, includeFieldNames = false)
public class FuelType implements Serializable {

    private static final long serialVersionUID = 1L;

    @Getter
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private final Long id;

    @Getter
    @NotNull
    @Length(min = 1, max = 30)
    @Column(name = "type", nullable = false, unique = true, updatable = false, length = 30)
    private final String type;

    @Getter
    @NotNull
    @Length(min = 1, max = 30)
    @Column(name = "english", nullable = false, unique = true, updatable = false, length = 30)
    private final String englishTranslation;

    @Getter
    @NotNull
    @Length(min = 1, max = 30)
    @Column(name = "polish", nullable = false, unique = true, updatable = false, length = 30)
    private final String polishTranslation;

    public static FuelType of(String type, String englishTranslation, String polishTranslation) {
        return new FuelType(type, englishTranslation, polishTranslation);
    }

    @PersistenceConstructor
    @SuppressWarnings("all")
    private FuelType() {
        this.id = null;
        this.type = null;
        this.englishTranslation = null;
        this.polishTranslation = null;
    }

    private FuelType(String type, String englishTranslation, String polishTranslation) {
        this.id = null;
        this.type = type;
        this.englishTranslation = englishTranslation;
        this.polishTranslation = polishTranslation;
    }
}
