package fr.thegostsniper.arffornia_proxy.motd;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MotdConfigTest {

    private static String bundledYaml() throws IOException {
        try (InputStream in = MotdConfigTest.class.getClassLoader().getResourceAsStream(MotdService.FILE_NAME)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<String> ids(List<MotdEntry> entries) {
        return entries.stream().map(MotdEntry::id).collect(Collectors.toList());
    }

    @Test
    void bundledFileParsesAndEveryEntryHasEnglishAndFrench() throws IOException {
        MotdConfig config = MotdConfig.parse(bundledYaml());
        assertEquals("en", config.defaultLanguage());
        assertEquals(List.of("en", "fr"), config.supportedLanguages());
        assertFalse(config.entries().isEmpty());
        for (MotdEntry entry : config.entries()) {
            assertTrue(entry.lines().containsKey("en"), entry.id() + " has no en");
            assertTrue(entry.lines().containsKey("fr"), entry.id() + " has no fr");
        }
    }

    /** The server list is about 270 px wide: longer lines are cut off on screen. */
    @Test
    void everyBundledLineFitsTheServerList() throws IOException {
        MotdConfig config = MotdConfig.parse(bundledYaml());
        MiniMessage mm = MiniMessage.miniMessage();
        PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
        List<String> headers = new java.util.ArrayList<>(List.of(config.header()));
        for (MotdEntry entry : config.entries()) {
            if (entry.header() != null) {
                headers.add(entry.header());
            }
            for (String line : entry.lines().values()) {
                String text = plain.serialize(mm.deserialize(line));
                assertTrue(text.length() <= 48, entry.id() + " is too long (" + text.length() + "): " + text);
            }
        }
        for (String header : headers) {
            String text = plain.serialize(mm.deserialize(header));
            assertTrue(text.length() <= 48, "header too long (" + text.length() + "): " + text);
        }
    }

    @Test
    void seasonalEntriesReplaceQuotes() throws IOException {
        List<MotdEntry> entries = MotdConfig.parse(bundledYaml()).entries();
        assertEquals(List.of("halloween-mining", "halloween-wither"),
                ids(MotdSelector.candidates(entries, LocalDate.of(2026, 10, 31))));
        assertEquals(List.of("new-year"), ids(MotdSelector.candidates(entries, LocalDate.of(2027, 1, 1))));
        assertEquals(List.of("april-fools"), ids(MotdSelector.candidates(entries, LocalDate.of(2027, 4, 1))));
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
