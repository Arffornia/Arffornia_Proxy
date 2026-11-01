package fr.thegostsniper.arffornia_proxy.motd;

import java.util.Map;

/**
 * One MOTD candidate: the second line of the server list entry, per language, plus an
 * optional first-line override (for seasonal styling).
 *
 * @param id       a short name, used in logs and in the preview command
 * @param schedule when the entry is active
 * @param priority among active entries, only the highest priority is kept (dated entries
 *                 default to 10, plain quotes to 0)
 * @param weight   relative chance among the kept entries (default 1)
 * @param header   first line override in MiniMessage, or {@code null} for the default header
 * @param lines    second line per language code ({@code en}, {@code fr}), MiniMessage
 */
public record MotdEntry(String id, Schedule schedule, int priority, int weight, String header,
                        Map<String, String> lines) {

    public MotdEntry {
        lines = Map.copyOf(lines);
    }

    /** The line for {@code language}, else the fallback language, else any language. */
    public String lineFor(String language, String fallbackLanguage) {
        String line = lines.get(language);
        if (line == null) {
            line = lines.get(fallbackLanguage);
        }
        if (line == null && !lines.isEmpty()) {
            line = lines.values().iterator().next();
        }
        return line == null ? "" : line;
    }
}
