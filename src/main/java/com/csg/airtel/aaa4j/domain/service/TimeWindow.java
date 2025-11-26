package com.csg.airtel.aaa4j.domain.service;

import java.time.LocalTime;
import java.util.Objects;

/**
 * Represents a time window with start and end times.
 * Supports only 24-hour format with hours only:
 * - Hour-only format: "00-24", "08-24", "0-12" (where 24 means end of day 23:59:59)
 */
public class TimeWindow {

    private final LocalTime startTime;
    private final LocalTime endTime;
    private final boolean crossesMidnight;

    /**
     * Creates a TimeWindow from a time window string.
     * Only supports 24-hour format with hours only (e.g., "00-24", "08-18", "0-12").
     *
     * @param timeWindowString the time window string in format "HH-HH" where HH is 0-24
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
                ". Expected format: 'HH-HH' (e.g., '00-24', '08-18', '0-12')");
        }

        String startStr = parts[0].trim();
        String endStr = parts[1].trim();

        LocalTime startTime = parseHourOnly(startStr);
        LocalTime endTime = parseHourOnly(endStr);

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
     * Parses a time string in hour-only format (0-24).
     *
     * @param timeStr the time string (e.g., "0", "8", "24")
     * @return LocalTime representing the hour (24 becomes 23:59:59)
     * @throws IllegalArgumentException if format is invalid or hour is out of range
     */
    private static LocalTime parseHourOnly(String timeStr) {
        timeStr = timeStr.trim();

        if (timeStr.isEmpty()) {
            throw new IllegalArgumentException("Time string cannot be empty");
        }

        // Only accept hour-only format (1 or 2 digits)
        if (!timeStr.matches("\\d{1,2}")) {
            throw new IllegalArgumentException("Invalid time format: " + timeStr +
                ". Only hour-only format is supported (0-24)");
        }

        try {
            int hour = Integer.parseInt(timeStr);

            // Special case: 24 means end of day (23:59:59)
            if (hour == 24) {
                return LocalTime.of(23, 59, 59);
            }

            if (hour < 0 || hour > 23) {
                throw new IllegalArgumentException("Hour must be between 0 and 24, got: " + hour);
            }

            return LocalTime.of(hour, 0);

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Unable to parse hour: " + timeStr +
                ". Expected format: single or double digit hour (0-24)", e);
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
