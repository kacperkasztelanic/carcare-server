package com.kasztelanic.carcare.web.rest.vm;

import ch.qos.logback.classic.Logger;
import lombok.ToString;
import lombok.Value;

/**
 * View Model object for storing a Logback logger.
 */
@Value(staticConstructor = "of")
@ToString(includeFieldNames = false)
public class LoggerVM {

    String name;
    String level;

    public static LoggerVM of(Logger logger) {
        return new LoggerVM(logger.getName(), logger.getEffectiveLevel().toString());
    }
}
