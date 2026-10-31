package fr.thegostsniper.arffornia_proxy.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerSettingsChangedEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.proxy.server.ServerPing;
import fr.thegostsniper.arffornia_proxy.motd.LanguageMemory;
import fr.thegostsniper.arffornia_proxy.motd.MotdConfig;
import fr.thegostsniper.arffornia_proxy.motd.MotdService;

import java.net.InetAddress;

/**
 * Replaces the server list description with the entry chosen by {@link MotdService}, in the
 * language last used from the pinging address when it is known.
 */
public class MotdListener {

    private final MotdService motd;
    private final LanguageMemory languages;

    public MotdListener(MotdService motd, LanguageMemory languages) {
        this.motd = motd;
        this.languages = languages;
    }

    @Subscribe
    public void onPing(ProxyPingEvent event) {
        motd.reloadIfChanged();
        InetAddress address = event.getConnection().getRemoteAddress().getAddress();
        boolean remember = motd.config().map(MotdConfig::rememberLanguage).orElse(false);
        String language = remember
                ? languages.recall(address).orElseGet(() -> motd.languageFor(null))
                : motd.languageFor(null);
        motd.render(motd.today(), language).ifPresent(description -> {
            ServerPing ping = event.getPing().asBuilder().description(description).build();
            event.setPing(ping);
        });
    }

    @Subscribe
    public void onSettings(PlayerSettingsChangedEvent event) {
        String language = motd.languageFor(event.getPlayerSettings().getLocale().toString());
        languages.remember(event.getPlayer().getRemoteAddress().getAddress(), language);
    }
}
