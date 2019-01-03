package com.kasztelanic.carcare.util;

import java.util.UUID;

public class UuidProvider {

    public static String newUuid() {
        return UUID.randomUUID().toString();
    }

    private UuidProvider() {
    }
}
