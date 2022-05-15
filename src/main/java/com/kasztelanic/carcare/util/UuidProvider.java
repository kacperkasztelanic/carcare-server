package com.kasztelanic.carcare.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.UUID;

//TODO make it a bean
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UuidProvider {

    public static String newUuid() {
        return UUID.randomUUID().toString();
    }
}
