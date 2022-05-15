package com.kasztelanic.carcare.web.rest.vm;

import ch.qos.logback.classic.Logger;
import lombok.ToString;
import lombok.Value;

/**
 * View Model object for storing a Logback logger.
 */
@Value(staticConstructor = "of")
@ToString(includeFieldNames = false)
public class LoggerVm {

    String name;
    String level;

    public static LoggerVm of(Logger logger) {
        return new LoggerVm(logger.getName(), logger.getEffectiveLevel().toString());
    }
}
