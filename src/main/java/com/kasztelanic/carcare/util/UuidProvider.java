package com.kasztelanic.carcare.util;

import java.util.UUID;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

//TODO make it a bean
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UuidProvider {

    public static String newUuid() {
        return UUID.randomUUID().toString();
    }
}
