package fr.thegostsniper.arffornia_proxy.motd;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MotdConfigTest {

    /**
     * A small sample in the production format. The real content, and the tests that check
     * it (both languages, line length), live in Arffornia_Provisioning next to motd.yml.
     */
    private static final String SAMPLE = """
            settings:
              default-language: en
              languages: [en, fr]
              timezone: Europe/Paris
              header: "<bold>Arffornia</bold>"
            entries:
              - id: creeper-feelings
                en: "<green>Creepers have feelings."
                fr: "<green>Les creepers ont du cœur."
              - id: new-year
                dates: "12-31..01-02"
                en: "Happy New Year"
                fr: "Bonne année"
              - id: halloween-mining
                dates: "10-24..10-31"
                en: "Something is mining"
                fr: "Quelque chose creuse"
              - id: halloween-wither
                dates: "10-24..10-31"
                en: "Trick or treat"
                fr: "Un bonbon ou un sort"
            """;

    private static List<String> ids(List<MotdEntry> entries) {
        return entries.stream().map(MotdEntry::id).collect(Collectors.toList());
    }

    @Test
    void sampleParses() throws IOException {
        MotdConfig config = MotdConfig.parse(SAMPLE);
        assertEquals("en", config.defaultLanguage());
        assertEquals(List.of("en", "fr"), config.supportedLanguages());
        assertEquals(4, config.entries().size());
    }

    @Test
    void seasonalEntriesReplaceQuotes() throws IOException {
        List<MotdEntry> entries = MotdConfig.parse(SAMPLE).entries();
        assertEquals(List.of("halloween-mining", "halloween-wither"),
                ids(MotdSelector.candidates(entries, LocalDate.of(2026, 10, 31))));
        assertEquals(List.of("new-year"), ids(MotdSelector.candidates(entries, LocalDate.of(2027, 1, 1))));
        List<String> ordinary = ids(MotdSelector.candidates(entries, LocalDate.of(2027, 3, 3)));
        assertTrue(ordinary.contains("creeper-feelings"));
        assertFalse(ordinary.contains("halloween-mining"));
    }

    @Test
    void textKeyAppliesToAllLanguagesAndPriorityIsExplicit() throws IOException {
        MotdConfig config = MotdConfig.parse("""
                entries:
                  - id: plain
                    text: "<gold>Hello"
                  - id: launch
                    dates: "2027-01-09"
                    priority: 20
                    en: "Launch"
                    fr: "Lancement"
                  - id: yearly
                    dates: "01-09"
                    en: "Yearly"
                """);
        assertEquals("<gold>Hello", config.entries().get(0).lineFor("fr", "en"));
        assertEquals(List.of("launch"), ids(MotdSelector.candidates(config.entries(), LocalDate.of(2027, 1, 9))));
        assertEquals(List.of("yearly"), ids(MotdSelector.candidates(config.entries(), LocalDate.of(2028, 1, 9))));
        assertEquals("Yearly", config.entries().get(2).lineFor("fr", "en"), "falls back to the default language");
    }

    @Test
    void weightsAreHonoured() throws IOException {
        MotdConfig config = MotdConfig.parse("""
                entries:
                  - id: rare
                    text: "rare"
                  - id: common
                    text: "common"
                    weight: 9
                """);
        Random random = new Random(42);
        int common = 0;
        for (int i = 0; i < 1000; i++) {
            if (MotdSelector.pick(config.entries(), LocalDate.of(2027, 5, 5), random).orElseThrow().id().equals("common")) {
                common++;
            }
        }
        assertTrue(common > 850 && common < 950, "common picked " + common + " times out of 1000");
    }

    @Test
    void malformedEntryNamesTheEntry() {
        IOException e = assertThrows(IOException.class, () -> MotdConfig.parse("""
                entries:
                  - id: broken
                    dates: "31-12"
                    text: "x"
                """));
        assertTrue(e.getMessage().contains("broken"), e.getMessage());
        assertThrows(IOException.class, () -> MotdConfig.parse("""
                entries:
                  - id: empty
                """));
    }
}
