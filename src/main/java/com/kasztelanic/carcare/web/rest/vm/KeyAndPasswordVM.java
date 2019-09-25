package com.kasztelanic.carcare.web.rest.vm;

import lombok.Getter;
import lombok.Setter;

/**
 * View Model object for storing the user's key and password.
 */
public class KeyAndPasswordVM {

    @Getter
    @Setter
    private String key;

    @Getter
    @Setter
    private String newPassword;
}
