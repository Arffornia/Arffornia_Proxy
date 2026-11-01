package fr.thegostsniper.arffornia_proxy.motd;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduleTest {

    @Test
    void emptyMeansAlways() {
        assertTrue(Schedule.parse("").matches(LocalDate.of(2026, 3, 3)));
        assertTrue(Schedule.parse(null).isAlways());
    }

    @Test
    void yearlyDayMatchesEveryYear() {
        Schedule s = Schedule.parse("12-25");
        assertTrue(s.matches(LocalDate.of(2026, 12, 25)));
        assertTrue(s.matches(LocalDate.of(2031, 12, 25)));
        assertFalse(s.matches(LocalDate.of(2026, 12, 24)));
    }

    @Test
    void yearlyRangeIsInclusive() {
        Schedule s = Schedule.parse("10-24..10-31");
        assertTrue(s.matches(LocalDate.of(2026, 10, 24)));
        assertTrue(s.matches(LocalDate.of(2026, 10, 31)));
        assertFalse(s.matches(LocalDate.of(2026, 11, 1)));
        assertFalse(s.matches(LocalDate.of(2026, 10, 23)));
    }

    @Test
    void yearlyRangeWrapsTheNewYear() {
        Schedule s = Schedule.parse("12-31..01-02");
        assertTrue(s.matches(LocalDate.of(2026, 12, 31)));
        assertTrue(s.matches(LocalDate.of(2027, 1, 1)));
        assertTrue(s.matches(LocalDate.of(2027, 1, 2)));
        assertFalse(s.matches(LocalDate.of(2027, 1, 3)));
        assertFalse(s.matches(LocalDate.of(2026, 12, 30)));
    }

    @Test
    void exactDatesMatchOnlyThatYear() {
        Schedule day = Schedule.parse("2027-04-03");
        assertTrue(day.matches(LocalDate.of(2027, 4, 3)));
        assertFalse(day.matches(LocalDate.of(2028, 4, 3)));

        Schedule range = Schedule.parse("2026-12-30..2027-01-02");
        assertTrue(range.matches(LocalDate.of(2027, 1, 1)));
        assertFalse(range.matches(LocalDate.of(2027, 12, 31)));
    }

    @Test
    void leapDayOnlyInLeapYears() {
        Schedule s = Schedule.parse("02-29");
        assertTrue(s.matches(LocalDate.of(2028, 2, 29)));
        assertFalse(s.matches(LocalDate.of(2027, 2, 28)));
    }

    @Test
    void rejectsMalformedValues() {
        assertThrows(IllegalArgumentException.class, () -> Schedule.parse("13-01"));
        assertThrows(IllegalArgumentException.class, () -> Schedule.parse("2026-12-01..12-31"));
        assertThrows(IllegalArgumentException.class, () -> Schedule.parse("2027-01-02..2026-12-01"));
        assertThrows(IllegalArgumentException.class, () -> Schedule.parse("01-01..01-02..01-03"));
        assertThrows(IllegalArgumentException.class, () -> Schedule.parse("christmas"));
    }
}
