package com.kasztelanic.carcare.repository.timezone;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@EqualsAndHashCode(of = { "id" })
@Entity
@Table(name = "jhi_date_time_wrapper")
public class DateTimeWrapper implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instant")
    private Instant instant;

    @Column(name = "local_date_time")
    private LocalDateTime localDateTime;

    @Column(name = "offset_date_time")
    private OffsetDateTime offsetDateTime;

    @Column(name = "zoned_date_time")
    private ZonedDateTime zonedDateTime;

    @Column(name = "local_time")
    private LocalTime localTime;

    // The test-only jhi_date_time_wrapper.offset_time column is created as a plain H2 TIME
    // (context="test" changeset in 00000000000000_initial_schema.xml), but Hibernate 6 defaults
    // OffsetTime to TIME_WITH_TIMEZONE. Force the pre-existing plain-TIME mapping instead of
    // touching the changelog.
    @Column(name = "offset_time")
    @JdbcTypeCode(SqlTypes.TIME)
    private OffsetTime offsetTime;

    @Column(name = "local_date")
    private LocalDate localDate;

    @Override
    public String toString() {
        return "TimeZoneTest{" +
            "id=" + id +
            ", instant=" + instant +
            ", localDateTime=" + localDateTime +
            ", offsetDateTime=" + offsetDateTime +
            ", zonedDateTime=" + zonedDateTime +
            '}';
    }
}
