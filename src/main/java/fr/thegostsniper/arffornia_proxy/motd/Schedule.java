package fr.thegostsniper.arffornia_proxy.motd;

import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * When a MOTD entry is active.
 *
 * <p>Accepted forms (the {@code dates} key of an entry):
 * <ul>
 *   <li>{@code ""} or absent: always active (a plain quote).</li>
 *   <li>{@code "12-25"}: that day, every year.</li>
 *   <li>{@code "10-24..10-31"}: that range, every year. A range may wrap the new year
 *       ({@code "12-31..01-02"}).</li>
 *   <li>{@code "2027-04-03"}: that exact day only.</li>
 *   <li>{@code "2026-12-01..2027-01-15"}: that exact range only.</li>
 * </ul>
 * Bounds are inclusive. {@code 02-29} only matches in leap years.
 */
public final class Schedule {

    private enum Kind { ALWAYS, YEARLY, EXACT }

    private final Kind kind;
    private final MonthDay fromDay;
    private final MonthDay toDay;
    private final LocalDate fromDate;
    private final LocalDate toDate;
    private final String source;

    private Schedule(Kind kind, MonthDay fromDay, MonthDay toDay, LocalDate fromDate, LocalDate toDate, String source) {
        this.kind = kind;
        this.fromDay = fromDay;
        this.toDay = toDay;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.source = source;
    }

    public static Schedule always() {
        return new Schedule(Kind.ALWAYS, null, null, null, null, "");
    }

    /**
     * Parses a {@code dates} value.
     *
     * @throws IllegalArgumentException if the value is not one of the accepted forms.
     */
    public static Schedule parse(String value) {
        if (value == null || value.isBlank()) {
            return always();
        }
        String text = value.trim();
        String[] parts = text.split("\\.\\.", -1);
        if (parts.length > 2) {
            throw new IllegalArgumentException("Too many '..' in dates: " + text);
        }
        String from = parts[0].trim();
        String to = parts.length == 2 ? parts[1].trim() : from;
        try {
            if (isExactDate(from) && isExactDate(to)) {
                LocalDate start = LocalDate.parse(from);
                LocalDate end = LocalDate.parse(to);
                if (end.isBefore(start)) {
                    throw new IllegalArgumentException("Exact date range ends before it starts: " + text);
                }
                return new Schedule(Kind.EXACT, null, null, start, end, text);
            }
            if (!isExactDate(from) && !isExactDate(to)) {
                return new Schedule(Kind.YEARLY, parseMonthDay(from), parseMonthDay(to), null, null, text);
            }
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid dates '" + text + "': " + e.getMessage(), e);
        }
        throw new IllegalArgumentException("Do not mix MM-DD and YYYY-MM-DD in one range: " + text);
    }

    private static boolean isExactDate(String s) {
        return s.length() == 10;
    }

    private static MonthDay parseMonthDay(String s) {
        return MonthDay.parse("--" + s);
    }

    /** Whether the entry is active on {@code day}. */
    public boolean matches(LocalDate day) {
        switch (kind) {
            case ALWAYS:
                return true;
            case EXACT:
                return !day.isBefore(fromDate) && !day.isAfter(toDate);
            case YEARLY:
            default:
                MonthDay md = MonthDay.from(day);
                if (!fromDay.isAfter(toDay)) {
                    return !md.isBefore(fromDay) && !md.isAfter(toDay);
                }
                // Wraps the new year, e.g. 12-31..01-02.
                return !md.isBefore(fromDay) || !md.isAfter(toDay);
        }
    }

    public boolean isAlways() {
        return kind == Kind.ALWAYS;
    }

    @Override
    public String toString() {
        return isAlways() ? "always" : source.toLowerCase(Locale.ROOT);
    }
}
