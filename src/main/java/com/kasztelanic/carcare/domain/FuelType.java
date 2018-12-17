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
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private final Long id;

    @Getter
    @NotNull
    @Length(min = 1, max = 30)
    @Column(name = "type", nullable = false, unique = true, updatable = false, length = 30)
    private final String type;

    public static FuelType of(String type) {
        return new FuelType(type);
    }

    @PersistenceConstructor
    @SuppressWarnings("all")
    private FuelType() {
        this.id = null;
        this.type = null;
    }

    private FuelType(String type) {
        this.id = null;
        this.type = type;
    }
}
