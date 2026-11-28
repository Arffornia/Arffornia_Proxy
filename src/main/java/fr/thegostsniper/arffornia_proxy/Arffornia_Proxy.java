package fr.thegostsniper.arffornia_proxy;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import fr.thegostsniper.arffornia_proxy.config.PluginConfig;
import fr.thegostsniper.arffornia_proxy.listener.MotdListener;
import fr.thegostsniper.arffornia_proxy.listener.VoteListener;
import fr.thegostsniper.arffornia_proxy.motd.LanguageMemory;
import fr.thegostsniper.arffornia_proxy.motd.MotdPreviewCommand;
import fr.thegostsniper.arffornia_proxy.motd.MotdService;
import fr.thegostsniper.arffornia_proxy.service.ApiService;
import fr.thegostsniper.arffornia_proxy.vote.VoteDispatcher;
import fr.thegostsniper.arffornia_proxy.vote.VoteQueue;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.random.RandomGenerator;

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
    private static final int VOTE_QUEUE_MAX_SIZE = 1000;

    private final Path dataDirectory;
    private ScheduledExecutorService voteExecutor;

    @Inject
    public Arffornia_Proxy(Logger logger, ProxyServer server, @DataDirectory Path dataDirectory) {
        this.logger = logger;
        this.server = server;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        // Server-list MOTD: independent of the vote settings, so it works even without them.
        MotdService motd = new MotdService(logger, dataDirectory, Clock.systemDefaultZone(), RandomGenerator.getDefault());
        motd.init();
        server.getEventManager().register(this, new MotdListener(motd, new LanguageMemory(Duration.ofDays(30), 10_000)));
        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("motdpreview").plugin(this).build(),
                new MotdPreviewCommand(motd));

        // 1. Initialize and load configuration
        PluginConfig config = new PluginConfig(logger, dataDirectory);
        if (!config.load()) {
            logger.error("Failed to load plugin configuration. The plugin will not be enabled.");
            return;
        }

        // 2. Initialize ApiService
        ApiService apiService = new ApiService(logger, server, config);

        // 3. Deliver votes through a persistent queue, then register the event listener
        voteExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "arffornia-vote-dispatcher");
            thread.setDaemon(true);
            return thread;
        });
        VoteQueue voteQueue = new VoteQueue(logger, dataDirectory.resolve("pending-votes.json"), VOTE_QUEUE_MAX_SIZE);
        VoteDispatcher dispatcher = new VoteDispatcher(logger, voteQueue,
                vote -> apiService.sendVote(vote.id(), vote.username(), vote.serviceName(), vote.address()),
                voteExecutor, Duration.ofSeconds(5), Duration.ofMinutes(5));
        dispatcher.start();
        server.getEventManager().register(this, new VoteListener(logger, dispatcher));

        logger.info("Arffornia Proxy Plugin has been enabled successfully.");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (voteExecutor != null) {
            voteExecutor.shutdownNow();
        }
    }
}
