package com.kasztelanic.carcare.web.rest.vm;

import lombok.ToString;
import lombok.Value;

/**
 * View Model object for storing the user's key and password.
 */
@Value(staticConstructor = "of")
@ToString(of = "key", includeFieldNames = false)
public class KeyAndPasswordVM {

    String key;
    String newPassword;
}
