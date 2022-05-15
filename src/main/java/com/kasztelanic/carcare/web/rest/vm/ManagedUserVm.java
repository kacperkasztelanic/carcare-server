package com.kasztelanic.carcare.web.rest.vm;

import com.kasztelanic.carcare.service.dto.UserDto;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Size;

/**
 * View Model extending the UserDto, which is meant to be used in the user management UI.
 */
public class ManagedUserVm extends UserDto {

    public static final int PASSWORD_MIN_LENGTH = 4;
    public static final int PASSWORD_MAX_LENGTH = 100;

    @Getter
    @Setter
    @Size(min = PASSWORD_MIN_LENGTH, max = PASSWORD_MAX_LENGTH)
    private String password;

    @Override
    public String toString() {
        return "ManagedUserVM(" + super.toString() + ")";
    }
}
