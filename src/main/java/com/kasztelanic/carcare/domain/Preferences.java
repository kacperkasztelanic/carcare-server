//package com.kasztelanic.carcare.domain;
//
//import java.io.Serializable;
//
//import javax.persistence.Column;
//import javax.persistence.Entity;
//import javax.persistence.GeneratedValue;
//import javax.persistence.GenerationType;
//import javax.persistence.Id;
//import javax.persistence.OneToOne;
//import javax.persistence.Table;
//import javax.validation.constraints.NotNull;
//
//import org.hibernate.annotations.Cache;
//import org.hibernate.annotations.CacheConcurrencyStrategy;
//import org.springframework.data.annotation.PersistenceConstructor;
//
//import com.fasterxml.jackson.annotation.JsonIgnore;
//import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
//
//import lombok.Builder;
//import lombok.EqualsAndHashCode;
//import lombok.Getter;
//import lombok.Setter;
//import lombok.ToString;
//
//@Entity
//@Table(name = "preferences")
//@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
//@EqualsAndHashCode(of = { "user" })
//@ToString
//public class Preferences implements Serializable {
//
//    private static final long serialVersionUID = 1L;
//
//    @Getter
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
//    private final Long id;
//
//    @Getter
//    @Setter
//    @Column(name = "inspection_reminder", nullable = false)
//    private boolean inspectionReminder;
//
//    @Getter
//    @Setter
//    @Column(name = "insurance_reminder", nullable = false)
//    private boolean insuranceReminder;
//
//    @Getter
//    @Setter
//    @Column(name = "service_reminder", nullable = false)
//    private boolean routineServiceReminder;
//
//    @Getter
//    @Setter
//    @JsonIgnore
//    @JsonIgnoreProperties("")
//    @NotNull
//    @OneToOne(optional = false)
//    private User user;
//
//    @PersistenceConstructor
//    @SuppressWarnings("all")
//    private Preferences() {
//        this.id = null;
//    }
//
//    @Builder
//    private Preferences(boolean inspectionReminder, boolean insuranceReminder, boolean routineServiceReminder,
//            User user) {
//        this.inspectionReminder = inspectionReminder;
//        this.insuranceReminder = insuranceReminder;
//        this.routineServiceReminder = routineServiceReminder;
//        this.user = user;
//        this.id = null;
//    }
//}
