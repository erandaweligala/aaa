package com.csg.airtel.aaa4j.domain.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TimeWindow Tests")
class TimeWindowTest {

    @Test
    @DisplayName("Should parse hour-only format: 00-24")
    void shouldParseHourOnlyFormat_00_24() {
        TimeWindow window = TimeWindow.parse("00-24");

        assertThat(window.getStartTime()).isEqualTo(LocalTime.of(0, 0));
        assertThat(window.getEndTime()).isEqualTo(LocalTime.of(23, 59, 59));
        assertThat(window.crossesMidnight()).isFalse();
    }

    @Test
    @DisplayName("Should parse hour-only format: 08-24")
    void shouldParseHourOnlyFormat_08_24() {
        TimeWindow window = TimeWindow.parse("08-24");

        assertThat(window.getStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(window.getEndTime()).isEqualTo(LocalTime.of(23, 59, 59));
        assertThat(window.crossesMidnight()).isFalse();
    }

    @Test
    @DisplayName("Should parse hour-only format: 0-12")
    void shouldParseHourOnlyFormat_0_12() {
        TimeWindow window = TimeWindow.parse("0-12");

        assertThat(window.getStartTime()).isEqualTo(LocalTime.of(0, 0));
        assertThat(window.getEndTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(window.crossesMidnight()).isFalse();
    }

    @Test
    @DisplayName("Should parse hour-only format that crosses midnight: 18-6")
    void shouldParseHourOnlyFormatCrossingMidnight() {
        TimeWindow window = TimeWindow.parse("18-6");

        assertThat(window.getStartTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(window.getEndTime()).isEqualTo(LocalTime.of(6, 0));
        assertThat(window.crossesMidnight()).isTrue();
    }

    @Test
    @DisplayName("Should handle whitespace in time window string")
    void shouldHandleWhitespace() {
        TimeWindow window = TimeWindow.parse("  08  -  18  ");

        assertThat(window.getStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(window.getEndTime()).isEqualTo(LocalTime.of(18, 0));
    }

    @ParameterizedTest
    @DisplayName("Should correctly identify time within normal window")
    @CsvSource({
        "8-18, 08:00, true",
        "8-18, 12:00, true",
        "8-18, 18:00, true",
        "8-18, 07:59, false",
        "8-18, 18:01, false",
        "8-18, 06:00, false",
        "8-18, 20:00, false"
    })
    void shouldCheckTimeInNormalWindow(String windowStr, String timeStr, boolean expected) {
        TimeWindow window = TimeWindow.parse(windowStr);
        LocalTime time = LocalTime.parse(timeStr);

        assertThat(window.contains(time)).isEqualTo(expected);
    }

    @ParameterizedTest
    @DisplayName("Should correctly identify time within midnight-crossing window")
    @CsvSource({
        "18-6, 18:00, true",
        "18-6, 23:59, true",
        "18-6, 00:00, true",
        "18-6, 03:00, true",
        "18-6, 06:00, true",
        "18-6, 12:00, false",
        "18-6, 17:59, false",
        "18-6, 06:01, false"
    })
    void shouldCheckTimeInMidnightCrossingWindow(String windowStr, String timeStr, boolean expected) {
        TimeWindow window = TimeWindow.parse(windowStr);
        LocalTime time = LocalTime.parse(timeStr);

        assertThat(window.contains(time)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Should handle full day window: 00-24")
    void shouldHandleFullDayWindow() {
        TimeWindow window = TimeWindow.parse("00-24");

        // Any time should be within a full day window
        assertThat(window.contains(LocalTime.of(0, 0))).isTrue();
        assertThat(window.contains(LocalTime.of(12, 0))).isTrue();
        assertThat(window.contains(LocalTime.of(23, 59))).isTrue();
    }

    @ParameterizedTest
    @DisplayName("Should throw exception for invalid formats")
    @ValueSource(strings = {
        "",
        "   ",
        "invalid",
        "08:00",
        "08:00-",
        "-18:00",
        "08:00-18:00-20:00",
        "25-12",
        "-1-12",
        "08:00-25:00",
        "invalid-format",
        "08:00-18:00",  // 24-hour format with minutes no longer supported
        "18:00-06:00",  // 24-hour format with minutes no longer supported
        "6PM-6AM",      // 12-hour format no longer supported
        "8:30AM-6:45PM" // 12-hour format with minutes no longer supported
    })
    void shouldThrowExceptionForInvalidFormats(String invalidWindow) {
        assertThatThrownBy(() -> TimeWindow.parse(invalidWindow))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should throw exception for null time window")
    void shouldThrowExceptionForNull() {
        assertThatThrownBy(() -> TimeWindow.parse(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be null or empty");
    }

    @Test
    @DisplayName("Should have correct toString format")
    void shouldHaveCorrectToString() {
        TimeWindow normalWindow = TimeWindow.parse("8-18");
        TimeWindow midnightWindow = TimeWindow.parse("18-6");

        assertThat(normalWindow.toString())
            .contains("08:00")
            .contains("18:00")
            .doesNotContain("crosses midnight");

        assertThat(midnightWindow.toString())
            .contains("18:00")
            .contains("06:00")
            .contains("crosses midnight");
    }

    @Test
    @DisplayName("Should implement equals and hashCode correctly")
    void shouldImplementEqualsAndHashCode() {
        TimeWindow window1 = TimeWindow.parse("8-18");
        TimeWindow window2 = TimeWindow.parse("08-18");
        TimeWindow window3 = TimeWindow.parse("9-17");

        assertThat(window1).isEqualTo(window2);
        assertThat(window1).isNotEqualTo(window3);
        assertThat(window1.hashCode()).isEqualTo(window2.hashCode());
    }

    @Test
    @DisplayName("Should handle single digit hours")
    void shouldHandleSingleDigitHours() {
        TimeWindow window = TimeWindow.parse("8-18");

        assertThat(window.getStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(window.getEndTime()).isEqualTo(LocalTime.of(18, 0));
    }

    @Test
    @DisplayName("Should handle boundary case: midnight to midnight")
    void shouldHandleMidnightToMidnight() {
        TimeWindow window = TimeWindow.parse("0-0");

        assertThat(window.getStartTime()).isEqualTo(LocalTime.of(0, 0));
        assertThat(window.getEndTime()).isEqualTo(LocalTime.of(0, 0));
        assertThat(window.crossesMidnight()).isFalse();
    }
}
