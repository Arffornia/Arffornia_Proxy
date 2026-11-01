package fr.thegostsniper.arffornia_proxy.motd;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Picks the entry to show on a given day: keep the active entries, keep only those with the
 * highest priority (so Halloween beats everyday quotes), then draw one by weight.
 */
public final class MotdSelector {

    private MotdSelector() {
    }

    /** All entries that could be shown on {@code day} (same rule as {@link #pick}, without the draw). */
    public static List<MotdEntry> candidates(List<MotdEntry> entries, LocalDate day) {
        int best = Integer.MIN_VALUE;
        List<MotdEntry> kept = new ArrayList<>();
        for (MotdEntry entry : entries) {
            if (!entry.schedule().matches(day)) {
                continue;
            }
            if (entry.priority() > best) {
                best = entry.priority();
                kept.clear();
            }
            if (entry.priority() == best) {
                kept.add(entry);
            }
        }
        return kept;
    }

    public static Optional<MotdEntry> pick(List<MotdEntry> entries, LocalDate day, RandomGenerator random) {
        List<MotdEntry> kept = candidates(entries, day);
        if (kept.isEmpty()) {
            return Optional.empty();
        }
        int total = 0;
        for (MotdEntry entry : kept) {
            total += entry.weight();
        }
        int roll = random.nextInt(total);
        for (MotdEntry entry : kept) {
            roll -= entry.weight();
            if (roll < 0) {
                return Optional.of(entry);
            }
        }
        return Optional.of(kept.get(kept.size() - 1));
    }
}
