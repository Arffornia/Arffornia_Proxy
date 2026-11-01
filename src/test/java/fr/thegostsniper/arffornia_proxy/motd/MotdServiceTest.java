package fr.thegostsniper.arffornia_proxy.motd;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MotdServiceTest {

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static MotdService service(Path dir) {
        MotdService service = new MotdService(NOPLogger.NOP_LOGGER, dir, Clock.systemUTC(), new Random(1));
        service.init();
        return service;
    }

    @Test
    void missingFileShowsTheBareFallbackAndCreatesNothing(@TempDir Path dir) {
        MotdService service = service(dir);
        assertFalse(service.loaded());
        assertEquals("Arffornia", plain(service.render(LocalDate.of(2027, 3, 3), "en")));
        assertFalse(Files.exists(dir.resolve(MotdService.FILE_NAME)), "the plugin must not write a default copy");
    }

    @Test
    void ageTagCountsYearsFromTheGivenYear(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve(MotdService.FILE_NAME), """
                settings:
                  header: "Arffornia"
                entries:
                  - id: anniversary
                    dates: "07-26"
                    en: "Arffornia turns <age:2016> today!"
                    fr: "Arffornia fête ses <age:2016> ans !"
                """);
        MotdService service = service(dir);
        assertTrue(service.loaded());
        assertEquals("Arffornia\nArffornia turns 10 today!", plain(service.render(LocalDate.of(2026, 7, 26), "en")));
        assertEquals("Arffornia\nArffornia fête ses 11 ans !", plain(service.render(LocalDate.of(2027, 7, 26), "fr")));
    }
}
