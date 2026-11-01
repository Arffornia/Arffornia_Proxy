package fr.thegostsniper.arffornia_proxy.motd;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.net.InetAddress;
import java.time.Duration;
import java.util.Optional;

/**
 * Remembers, in memory only, the client language of players who joined recently, keyed by
 * their address. The server list ping carries no language, so this is the only way to answer
 * a returning player in French or English.
 *
 * <p>Privacy: nothing is written to disk or logged; entries expire after {@code ttl}; the
 * cache holds at most {@code maxEntries}. Private, loopback and link-local addresses are
 * ignored: they mean the proxy does not see the real client address (for example a
 * Kubernetes Service with {@code externalTrafficPolicy: Cluster}), and remembering them
 * would give every player the language of the last one who joined.
 */
public final class LanguageMemory {

    private final Cache<InetAddress, String> cache;

    public LanguageMemory(Duration ttl, long maxEntries) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(maxEntries)
                .build();
    }

    public void remember(InetAddress address, String language) {
        if (isUsable(address) && language != null && !language.isBlank()) {
            cache.put(address, language);
        }
    }

    public Optional<String> recall(InetAddress address) {
        if (!isUsable(address)) {
            return Optional.empty();
        }
        return Optional.ofNullable(cache.getIfPresent(address));
    }

    static boolean isUsable(InetAddress address) {
        return address != null
                && !address.isLoopbackAddress()
                && !address.isSiteLocalAddress()
                && !address.isLinkLocalAddress()
                && !address.isAnyLocalAddress()
                && !isUniqueLocalV6(address);
    }

    private static boolean isUniqueLocalV6(InetAddress address) {
        byte[] raw = address.getAddress();
        return raw.length == 16 && (raw[0] & 0xfe) == 0xfc;
    }
}
