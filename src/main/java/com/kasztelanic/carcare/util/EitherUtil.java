package com.kasztelanic.carcare.util;

import io.vavr.control.Either;

public class EitherUtil {

    public static <E extends Throwable, T> T getOrThrow(Either<E, T> either) throws E {
        return either.get();
    }

    private EitherUtil() {
    }
}
