package fr.thegostsniper.arffornia_proxy;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import fr.thegostsniper.arffornia_proxy.config.PluginConfig;
import fr.thegostsniper.arffornia_proxy.listener.VoteListener;
import fr.thegostsniper.arffornia_proxy.service.ApiService;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(
        id = "arffornia_proxy",
        name = "Arffornia_Proxy",
        version = BuildConstants.VERSION,
        url = "https://arffornia.com",
        authors = {"TheGostsniperfr"},
        dependencies = {@Dependency(id = "nuvotifier")}
)
public class Arffornia_Proxy {

    private final Logger logger;
    private final ProxyServer server;
    private final Path dataDirectory;

    @Inject
    public Arffornia_Proxy(Logger logger, ProxyServer server, @DataDirectory Path dataDirectory) {
        this.logger = logger;
        this.server = server;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        // 1. Initialize and load configuration
        PluginConfig config = new PluginConfig(logger, dataDirectory);
        if (!config.load()) {
            logger.error("Failed to load plugin configuration. The plugin will not be enabled.");
            return;
        }

        // 2. Initialize ApiService
        ApiService apiService = new ApiService(logger, server, config);

        // 3. Register the event listener
        server.getEventManager().register(this, new VoteListener(logger, apiService));

        logger.info("Arffornia Proxy Plugin has been enabled successfully.");
    }
}