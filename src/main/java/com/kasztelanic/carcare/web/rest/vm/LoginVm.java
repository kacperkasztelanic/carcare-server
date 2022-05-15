package com.kasztelanic.carcare.web.rest.vm;

import lombok.ToString;
import lombok.Value;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * View Model object for storing a user's credentials.
 */
@Value(staticConstructor = "of")
@ToString(of = {"username", "rememberMe"}, includeFieldNames = false)
public class LoginVm {

    @NotNull
    @Size(min = 1, max = 50)
    String username;
    @NotNull
    @Size(min = ManagedUserVm.PASSWORD_MIN_LENGTH, max = ManagedUserVm.PASSWORD_MAX_LENGTH)
    String password;
    Boolean rememberMe;

    public static LoginVm of(String username, String password) {
        return new LoginVm(username, password, false);
    }
}
