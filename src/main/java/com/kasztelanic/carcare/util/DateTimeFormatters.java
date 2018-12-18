package com.kasztelanic.carcare.util;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

public class DateTimeFormatters {

    public static final String DATE_SPLIT_CHARACTER = "-";
    public static final String TIME_SPLIT_CHARACTER = ":";
    public static final String TIMESTAMP_SPLIT_CHARACTER = "_";

    private static final String TWO_DIGITS = "[0-9]{2}";
    private static final String FOUR_DIGITS = "[0-9]{4}";

    public static final DateTimeFormatter DATE_FORMATTER = new DateTimeFormatterBuilder().parseCaseSensitive()
            .parseStrict().appendValue(ChronoField.YEAR, 4).appendLiteral(DATE_SPLIT_CHARACTER)
            .appendValue(ChronoField.MONTH_OF_YEAR, 2).appendLiteral(DATE_SPLIT_CHARACTER)
            .appendValue(ChronoField.DAY_OF_MONTH, 2).toFormatter();

    public static final DateTimeFormatter TIME_FORMATTER = new DateTimeFormatterBuilder().parseCaseInsensitive()
            .parseLenient().appendValue(ChronoField.CLOCK_HOUR_OF_DAY, 2).appendLiteral(TIME_SPLIT_CHARACTER)
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2).appendLiteral(TIME_SPLIT_CHARACTER)
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2).toFormatter();

    public static final DateTimeFormatter TIMESTAMP_FORMATTER = new DateTimeFormatterBuilder().parseCaseInsensitive()
            .parseLenient().append(DATE_FORMATTER).appendLiteral(TIMESTAMP_SPLIT_CHARACTER).append(TIME_FORMATTER)
            .toFormatter();

    public static final String DATE_REGEX = FOUR_DIGITS + DATE_SPLIT_CHARACTER + TWO_DIGITS + DATE_SPLIT_CHARACTER
            + TWO_DIGITS;

    public static final String TIME_REGEX = TWO_DIGITS + TIME_SPLIT_CHARACTER + TWO_DIGITS + TIME_SPLIT_CHARACTER
            + TWO_DIGITS;

    public static final String TIMESTAMP_REGEX = DATE_REGEX + TIMESTAMP_SPLIT_CHARACTER + TIME_REGEX;

    private DateTimeFormatters() {
    }
}
