package com.kasztelanic.carcare.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.Table;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.kasztelanic.carcare.config.Constants;

import lombok.Getter;
import lombok.Setter;

/**
 * A user.
 */
@Entity
@Table(name = "jhi_user")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class User extends AbstractAuditingEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    @Setter
    private Long id;

    @NotNull
    @Pattern(regexp = Constants.LOGIN_REGEX)
    @Size(min = 1, max = 50)
    @Column(length = 50, unique = true, nullable = false)
    @Getter
    private String login;

    @JsonIgnore
    @NotNull
    @Size(min = 60, max = 60)
    @Column(name = "password_hash", length = 60, nullable = false)
    @Getter
    @Setter
    private String password;

    @Size(max = 50)
    @Column(name = "first_name", length = 50)
    @Getter
    @Setter
    private String firstName;

    @Size(max = 50)
    @Column(name = "last_name", length = 50)
    @Getter
    @Setter
    private String lastName;

    @Email
    @Size(min = 5, max = 254)
    @Column(length = 254, unique = true)
    @Getter
    @Setter
    private String email;

    @NotNull
    @Column(nullable = false)
    @Getter
    @Setter
    private boolean activated = false;

    @Size(min = 2, max = 6)
    @Column(name = "lang_key", length = 6)
    @Getter
    @Setter
    private String langKey;

    @Size(max = 256)
    @Column(name = "image_url", length = 256)
    @Getter
    @Setter
    private String imageUrl;

    @Size(max = 20)
    @Column(name = "activation_key", length = 20)
    @JsonIgnore
    @Getter
    @Setter
    private String activationKey;

    @Size(max = 20)
    @Column(name = "reset_key", length = 20)
    @JsonIgnore
    @Getter
    @Setter
    private String resetKey;

    @Column(name = "reset_date")
    @Getter
    @Setter
    private Instant resetDate = null;

    @JsonIgnore
    @ManyToMany
    @JoinTable(
        name = "jhi_user_authority",
        joinColumns = {@JoinColumn(name = "user_id", referencedColumnName = "id")},
        inverseJoinColumns = {@JoinColumn(name = "authority_name", referencedColumnName = "name")})
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @BatchSize(size = 20)
    @Getter
    @Setter
    private Set<Authority> authorities = new HashSet<>();

    // Lowercase the login before saving it in database
    public void setLogin(String login) {
        this.login = StringUtils.lowerCase(login, Locale.ENGLISH);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        User user = (User) o;
        return !(user.getId() == null || getId() == null) && Objects.equals(getId(), user.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "User{" + "login='" + login + '\'' + ", firstName='" + firstName + '\'' + ", lastName='" + lastName
                + '\'' + ", email='" + email + '\'' + ", imageUrl='" + imageUrl + '\'' + ", activated='" + activated
                + '\'' + ", langKey='" + langKey + '\'' + ", activationKey='" + activationKey + '\'' + "}";
    }
}
