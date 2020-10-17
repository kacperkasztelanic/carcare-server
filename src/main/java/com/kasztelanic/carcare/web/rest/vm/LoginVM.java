package com.kasztelanic.carcare.web.rest.vm;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import lombok.ToString;
import lombok.Value;

/**
 * View Model object for storing a user's credentials.
 */
@Value(staticConstructor = "of")
@ToString(of = {"username", "rememberMe"}, includeFieldNames = false)
public class LoginVM {

    @NotNull
    @Size(min = 1, max = 50)
    String username;
    @NotNull
    @Size(min = ManagedUserVM.PASSWORD_MIN_LENGTH, max = ManagedUserVM.PASSWORD_MAX_LENGTH)
    String password;
    Boolean rememberMe;

    public static LoginVM of(String username, String password) {
        return new LoginVM(username, password, false);
    }
}
