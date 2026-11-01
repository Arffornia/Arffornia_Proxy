package fr.thegostsniper.arffornia_proxy.motd;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LanguageMemoryTest {

    @Test
    void remembersPublicAddressesOnly() throws UnknownHostException {
        LanguageMemory memory = new LanguageMemory(Duration.ofDays(1), 10);
        InetAddress publicV4 = InetAddress.getByName("203.0.113.7");
        InetAddress natted = InetAddress.getByName("10.42.0.1");
        InetAddress uniqueLocal = InetAddress.getByName("fd00::1");

        memory.remember(publicV4, "fr");
        memory.remember(natted, "fr");
        memory.remember(uniqueLocal, "fr");

        assertEquals(Optional.of("fr"), memory.recall(publicV4));
        assertEquals(Optional.empty(), memory.recall(natted));
        assertEquals(Optional.empty(), memory.recall(uniqueLocal));
    }
}
