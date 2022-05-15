package com.kasztelanic.carcare.domain;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Immutable;
import org.springframework.data.annotation.PersistenceConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

@Entity
@Immutable
@Table(name = "reminder_advances")
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
@EqualsAndHashCode(of = {"days"})
@ToString(of = {"days"}, includeFieldNames = false)
public class ReminderAdvance implements Serializable {

    private static final long serialVersionUID = 1L;

    @Getter
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private final Long id;

    @Getter
    @NotNull
    @Min(0)
    @Column(name = "type", nullable = false, unique = true, updatable = false)
    private final Integer days;

    public static ReminderAdvance of(Integer days) {
        return new ReminderAdvance(days);
    }

    @PersistenceConstructor
    @SuppressWarnings("all")
    private ReminderAdvance() {
        this.id = null;
        this.days = null;
    }

    private ReminderAdvance(Integer days) {
        this.id = null;
        this.days = days;
    }
}
