package fr.thegostsniper.arffornia_proxy.listener;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.vexsoftware.votifier.velocity.event.VotifierEvent;
import fr.thegostsniper.arffornia_proxy.service.ApiService;

import org.slf4j.Logger;

/**
 * Listens for NuVotifier events and forwards them to the ApiService.
 */
public class VoteListener {

    private final Logger logger;
    private final ApiService apiService;

    @Inject
    public VoteListener(Logger logger, ApiService apiService) {
        this.logger = logger;
        this.apiService = apiService;
    }

    @Subscribe
    public void onVotifierEvent(VotifierEvent event) {
        logger.info("Received vote from service '{}' for player '{}'. Forwarding to web API.",
                event.getVote().getServiceName(),
                event.getVote().getUsername());

        apiService.sendVote(event.getVote());
    }
}