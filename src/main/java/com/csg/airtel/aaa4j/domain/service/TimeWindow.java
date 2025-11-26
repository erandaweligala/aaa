package com.csg.airtel.aaa4j.domain.service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;

/**
 * Represents a time window with start and end times.
 * Supports multiple time formats:
 * - Hour-only format: "00-24", "08-24" (where 24 means end of day 23:59:59)
 * - 24-hour format: "08:00-18:00", "18:00-06:00"
 * - 12-hour format: "6PM-6AM", "8:00AM-6:00PM"
 */
public class TimeWindow {

    private static final DateTimeFormatter TWELVE_HOUR_FORMATTER = DateTimeFormatter.ofPattern("h:mma");
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final boolean crossesMidnight;

    /**
     * Creates a TimeWindow from a time window string.
     *
     * @param timeWindowString the time window string (e.g., "00-24", "08:00-18:00", "6PM-6AM")
     * @return a TimeWindow instance
     * @throws IllegalArgumentException if the format is invalid
     */
    public static TimeWindow parse(String timeWindowString) {
        if (timeWindowString == null || timeWindowString.trim().isEmpty()) {
            throw new IllegalArgumentException("Time window string cannot be null or empty");
        }

        String[] parts = timeWindowString.split("-");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid time window format: " + timeWindowString +
                ". Expected format: 'start-end' (e.g., '00-24', '08:00-18:00', '6PM-6AM')");
        }

        String startStr = parts[0].trim();
        String endStr = parts[1].trim();

        LocalTime startTime = parseTime(startStr);
        LocalTime endTime = parseTime(endStr);

        return new TimeWindow(startTime, endTime);
    }

    private TimeWindow(LocalTime startTime, LocalTime endTime) {
        this.startTime = Objects.requireNonNull(startTime, "Start time cannot be null");
        this.endTime = Objects.requireNonNull(endTime, "End time cannot be null");
        this.crossesMidnight = startTime.isAfter(endTime);
    }

    /**
     * Checks if the current time falls within this time window.
     *
     * @return true if current time is within the window, false otherwise
     */
    public boolean containsNow() {
        return contains(LocalTime.now());
    }

    /**
     * Checks if a given time falls within this time window.
     *
     * @param time the time to check
     * @return true if the time is within the window, false otherwise
     */
    public boolean contains(LocalTime time) {
        if (crossesMidnight) {
            // Time window crosses midnight (e.g., 18:00-06:00)
            // Time is within window if it's >= start OR <= end
            return !time.isBefore(startTime) || !time.isAfter(endTime);
        } else {
            // Normal time window (e.g., 08:00-18:00)
            // Time is within window if it's >= start AND <= end
            return !time.isBefore(startTime) && !time.isAfter(endTime);
        }
    }

    /**
     * Parses a time string in various formats:
     * - Hour-only format: "0", "8", "24" (where 24 becomes 23:59:59)
     * - 24-hour format: "08:00", "18:30"
     * - 12-hour format: "6PM", "8:00AM"
     */
    private static LocalTime parseTime(String timeStr) {
        timeStr = timeStr.trim();

        if (timeStr.isEmpty()) {
            throw new IllegalArgumentException("Time string cannot be empty");
        }

        try {
            // Try hour-only format first (e.g., "0", "8", "24")
            if (timeStr.matches("\\d{1,2}")) {
                int hour = Integer.parseInt(timeStr);

                // Special case: 24 means end of day (23:59:59)
                if (hour == 24) {
                    return LocalTime.of(23, 59, 59);
                }

                if (hour < 0 || hour > 23) {
                    throw new IllegalArgumentException("Hour must be between 0 and 24, got: " + hour);
                }

                return LocalTime.of(hour, 0);
            }

            // Try 12-hour format with AM/PM
            String upperTime = timeStr.toUpperCase();
            if (upperTime.contains("AM") || upperTime.contains("PM")) {
                // Add :00 if no minutes specified (e.g., "6PM" -> "6:00PM")
                if (!timeStr.contains(":")) {
                    String meridiem = upperTime.substring(upperTime.length() - 2);
                    String hourPart = timeStr.substring(0, timeStr.length() - 2);
                    timeStr = hourPart + ":00" + meridiem;
                }
                return LocalTime.parse(timeStr.toUpperCase(), TWELVE_HOUR_FORMATTER);
            }

            // Try 24-hour format (e.g., "08:00", "18:30")
            return LocalTime.parse(timeStr);

        } catch (DateTimeParseException | NumberFormatException e) {
            throw new IllegalArgumentException("Unable to parse time: " + timeStr +
                ". Supported formats: hour-only (0-24), 24-hour (HH:mm), or 12-hour (h:mmAM/PM)", e);
        }
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public boolean crossesMidnight() {
        return crossesMidnight;
    }

    @Override
    public String toString() {
        return String.format("TimeWindow[%s-%s%s]",
            startTime,
            endTime,
            crossesMidnight ? " (crosses midnight)" : "");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TimeWindow that = (TimeWindow) o;
        return startTime.equals(that.startTime) && endTime.equals(that.endTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(startTime, endTime);
    }
}
