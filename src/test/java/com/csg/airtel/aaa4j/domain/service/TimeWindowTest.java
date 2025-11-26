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
    @DisplayName("Should parse 24-hour format: 08:00-18:00")
    void shouldParse24HourFormat() {
        TimeWindow window = TimeWindow.parse("08:00-18:00");

        assertThat(window.getStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(window.getEndTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(window.crossesMidnight()).isFalse();
    }

    @Test
    @DisplayName("Should parse 24-hour format that crosses midnight: 18:00-06:00")
    void shouldParse24HourFormatCrossingMidnight() {
        TimeWindow window = TimeWindow.parse("18:00-06:00");

        assertThat(window.getStartTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(window.getEndTime()).isEqualTo(LocalTime.of(6, 0));
        assertThat(window.crossesMidnight()).isTrue();
    }

    @Test
    @DisplayName("Should parse 12-hour format: 6PM-6AM")
    void shouldParse12HourFormat() {
        TimeWindow window = TimeWindow.parse("6PM-6AM");

        assertThat(window.getStartTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(window.getEndTime()).isEqualTo(LocalTime.of(6, 0));
        assertThat(window.crossesMidnight()).isTrue();
    }

    @Test
    @DisplayName("Should parse 12-hour format with minutes: 8:30AM-6:45PM")
    void shouldParse12HourFormatWithMinutes() {
        TimeWindow window = TimeWindow.parse("8:30AM-6:45PM");

        assertThat(window.getStartTime()).isEqualTo(LocalTime.of(8, 30));
        assertThat(window.getEndTime()).isEqualTo(LocalTime.of(18, 45));
        assertThat(window.crossesMidnight()).isFalse();
    }

    @Test
    @DisplayName("Should handle whitespace in time window string")
    void shouldHandleWhitespace() {
        TimeWindow window = TimeWindow.parse("  08:00  -  18:00  ");

        assertThat(window.getStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(window.getEndTime()).isEqualTo(LocalTime.of(18, 0));
    }

    @ParameterizedTest
    @DisplayName("Should correctly identify time within normal window")
    @CsvSource({
        "08:00-18:00, 08:00, true",
        "08:00-18:00, 12:00, true",
        "08:00-18:00, 18:00, true",
        "08:00-18:00, 07:59, false",
        "08:00-18:00, 18:01, false",
        "08:00-18:00, 06:00, false",
        "08:00-18:00, 20:00, false"
    })
    void shouldCheckTimeInNormalWindow(String windowStr, String timeStr, boolean expected) {
        TimeWindow window = TimeWindow.parse(windowStr);
        LocalTime time = LocalTime.parse(timeStr);

        assertThat(window.contains(time)).isEqualTo(expected);
    }

    @ParameterizedTest
    @DisplayName("Should correctly identify time within midnight-crossing window")
    @CsvSource({
        "18:00-06:00, 18:00, true",
        "18:00-06:00, 23:59, true",
        "18:00-06:00, 00:00, true",
        "18:00-06:00, 03:00, true",
        "18:00-06:00, 06:00, true",
        "18:00-06:00, 12:00, false",
        "18:00-06:00, 17:59, false",
        "18:00-06:00, 06:01, false"
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
        "invalid-format"
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
        TimeWindow normalWindow = TimeWindow.parse("08:00-18:00");
        TimeWindow midnightWindow = TimeWindow.parse("18:00-06:00");

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
        TimeWindow window1 = TimeWindow.parse("08:00-18:00");
        TimeWindow window2 = TimeWindow.parse("08:00-18:00");
        TimeWindow window3 = TimeWindow.parse("09:00-17:00");

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
    @DisplayName("Should handle mixed formats in same window string")
    void shouldHandleMixedFormats() {
        TimeWindow window1 = TimeWindow.parse("8-18:00");
        TimeWindow window2 = TimeWindow.parse("08:00-18");

        assertThat(window1.getStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(window1.getEndTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(window2.getStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(window2.getEndTime()).isEqualTo(LocalTime.of(18, 0));
    }

    @Test
    @DisplayName("Should handle lowercase AM/PM")
    void shouldHandleLowercaseAmPm() {
        TimeWindow window = TimeWindow.parse("6pm-6am");

        assertThat(window.getStartTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(window.getEndTime()).isEqualTo(LocalTime.of(6, 0));
    }

    @Test
    @DisplayName("Should handle boundary case: midnight to midnight")
    void shouldHandleMidnightToMidnight() {
        TimeWindow window = TimeWindow.parse("00:00-00:00");

        assertThat(window.getStartTime()).isEqualTo(LocalTime.of(0, 0));
        assertThat(window.getEndTime()).isEqualTo(LocalTime.of(0, 0));
        assertThat(window.crossesMidnight()).isFalse();
    }

    @Test
    @DisplayName("Should handle noon formats")
    void shouldHandleNoonFormats() {
        TimeWindow window = TimeWindow.parse("12:00PM-11:59PM");

        assertThat(window.getStartTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(window.getEndTime()).isEqualTo(LocalTime.of(23, 59));
    }
}
