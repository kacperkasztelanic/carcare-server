package com.kasztelanic.carcare.service.dto;

import com.kasztelanic.carcare.config.Constants;

import com.kasztelanic.carcare.domain.Authority;
import com.kasztelanic.carcare.domain.User;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;

import javax.validation.constraints.*;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Setter;

/**
 * A DTO representing a user, with his authorities.
 */
public class UserDto {

    @Getter
    @Setter
    private Long id;


    @NotBlank
    @Pattern(regexp = Constants.LOGIN_REGEX)
    @Size(min = 1, max = 50)
    @Getter
    @Setter
    private String login;

    @Size(max = 50)
    @Getter
    @Setter
    private String firstName;

    @Size(max = 50)
    @Getter
    @Setter
    private String lastName;

    @Email
    @Size(min = 5, max = 254)
    @Getter
    @Setter
    private String email;

    @Size(max = 256)
    @Getter
    @Setter
    private String imageUrl;

    @Getter
    @Setter
    private boolean activated = false;

    @Size(min = 2, max = 6)
    @Getter
    @Setter
    private String langKey;

    @Getter
    @Setter
    private String createdBy;

    @Getter
    @Setter
    private Instant createdDate;

    @Getter
    @Setter
    private String lastModifiedBy;

    @Getter
    @Setter
    private Instant lastModifiedDate;

    @Getter
    @Setter
    private Set<String> authorities;

    public UserDto() {
        // Empty constructor needed for Jackson.
    }

    public UserDto(User user) {
        this.id = user.getId();
        this.login = user.getLogin();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();
        this.email = user.getEmail();
        this.activated = user.isActivated();
        this.imageUrl = user.getImageUrl();
        this.langKey = user.getLangKey();
        this.createdBy = user.getCreatedBy();
        this.createdDate = user.getCreatedDate();
        this.lastModifiedBy = user.getLastModifiedBy();
        this.lastModifiedDate = user.getLastModifiedDate();
        this.authorities = user.getAuthorities().stream()
            .map(Authority::getName)
            .collect(Collectors.toSet());
    }

    @Override
    public String toString() {
        return "UserDto{" +
            "login='" + login + '\'' +
            ", firstName='" + firstName + '\'' +
            ", lastName='" + lastName + '\'' +
            ", email='" + email + '\'' +
            ", imageUrl='" + imageUrl + '\'' +
            ", activated=" + activated +
            ", langKey='" + langKey + '\'' +
            ", createdBy=" + createdBy +
            ", createdDate=" + createdDate +
            ", lastModifiedBy='" + lastModifiedBy + '\'' +
            ", lastModifiedDate=" + lastModifiedDate +
            ", authorities=" + authorities +
            "}";
    }
}
