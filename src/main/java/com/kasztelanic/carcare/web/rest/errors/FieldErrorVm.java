package com.kasztelanic.carcare.web.rest.errors;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serializable;

@AllArgsConstructor
public class FieldErrorVm implements Serializable {

    private static final long serialVersionUID = 1L;

    @Getter
    private final String objectName;
    @Getter
    private final String field;
    @Getter
    private final String message;
}
